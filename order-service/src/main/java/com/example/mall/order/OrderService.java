package com.example.mall.order;

import com.example.mall.common.ApiException;
import feign.FeignException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final UserClient users;
    private final ProductClient products;
    private final String internalKey;

    public OrderService(JdbcTemplate jdbc, TransactionTemplate transactions, UserClient users, ProductClient products,
                        @Value("${INTERNAL_KEY:mall-lab-internal-key-change-me}") String internalKey) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.users = users;
        this.products = products;
        this.internalKey = internalKey;
    }

    public record Item(long productId, String name, BigDecimal price, int quantity, BigDecimal subtotal) {}
    public record Cart(List<Item> items, BigDecimal total) {}
    public record Order(long id, String orderNo, String status, BigDecimal total, LocalDateTime createdAt, List<Item> items) {}
    private record OrderRow(long id, String orderNo, String status, BigDecimal total, LocalDateTime createdAt, String reservationKey) {}

    public long authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new ApiException(401, "请先登录");
        try {
            UserClient.User user = users.me(authorization, internalKey);
            if (user == null) throw new ApiException(401, "登录无效，请重新登录");
            return user.id();
        } catch (FeignException failure) {
            if (failure.status() == 401) throw new ApiException(401, "登录已过期，请重新登录");
            throw new ApiException(503, "身份服务暂不可用，请稍后重试");
        }
    }

    public Cart cart(long userId) {
        List<Item> items = quantities(userId).stream().map(quantity -> {
            ProductClient.Product product;
            try { product = products.get(quantity.productId(), internalKey); }
            catch (FeignException failure) { throw productError(failure); }
            return item(product.id(), product.name(), product.price(), quantity.quantity());
        }).toList();
        return new Cart(items, items.stream().map(Item::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public Cart put(long userId, long productId, int quantity) {
        return transactions.execute(status -> {
            lockUser(userId);
            ProductClient.Product product;
            try { product = products.get(productId, internalKey); }
            catch (FeignException failure) { throw productError(failure); }
            if (product.stock() < quantity) throw new ApiException(409, "商品库存不足");
            int updated = jdbc.update("UPDATE cart_items SET quantity=? WHERE user_id=? AND product_id=?", quantity, userId, productId);
            if (updated == 0) jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES(?,?,?)", userId, productId, quantity);
            return cart(userId);
        });
    }

    public Cart remove(long userId, long productId) {
        return transactions.execute(status -> {
            lockUser(userId);
            jdbc.update("DELETE FROM cart_items WHERE user_id=? AND product_id=?", userId, productId);
            return cart(userId);
        });
    }

    public Order create(long userId, String requestId) {
        String reservationKey = "order-" + userId + "-" + UUID.randomUUID();
        boolean[] reservationAttempted = { false };
        try {
            return transactions.execute(status -> {
                lockUser(userId);
                List<OrderRow> existing = jdbc.query("SELECT * FROM mall_orders WHERE user_id=? AND request_id=?",
                        (row, n) -> row(row), userId, requestId);
                if (!existing.isEmpty()) return order(existing.get(0));
                List<ProductClient.Quantity> quantities = quantities(userId);
                if (quantities.isEmpty()) throw new ApiException(400, "购物车为空");
                reservationAttempted[0] = true;
                ProductClient.Reservation reservation;
                try { reservation = products.reserve(new ProductClient.ReserveRequest(reservationKey, quantities), internalKey); }
                catch (FeignException failure) { throw productError(failure); }
                List<Item> items = reservation.items().stream()
                        .map(line -> item(line.productId(), line.name(), line.price(), line.quantity())).toList();
                BigDecimal total = items.stream().map(Item::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                String orderNo = "M" + UUID.randomUUID().toString().replace("-", "");
                GeneratedKeyHolder key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO mall_orders(order_no,user_id,request_id,reservation_key,status,total) VALUES(?,?,?,?,?,?)",
                            new String[]{"id"});
                    statement.setString(1, orderNo); statement.setLong(2, userId); statement.setString(3, requestId);
                    statement.setString(4, reservationKey); statement.setString(5, "PENDING"); statement.setBigDecimal(6, total);
                    return statement;
                }, key);
                long id = key.getKey().longValue();
                for (Item item : items) jdbc.update("INSERT INTO order_items(order_id,product_id,name,price,quantity) VALUES(?,?,?,?,?)",
                        id, item.productId(), item.name(), item.price(), item.quantity());
                jdbc.update("DELETE FROM cart_items WHERE user_id=?", userId);
                log.info("Order created orderId={} userId={} total={}", id, userId, total);
                return owned(userId, id);
            });
        } catch (RuntimeException failure) {
            // Executes after TransactionTemplate has rolled back the local transaction.
            if (reservationAttempted[0]) release(reservationKey);
            if (failure instanceof ApiException api) throw api;
            log.error("Order creation rolled back userId={} reservationKey={}", userId, reservationKey, failure);
            throw new ApiException(503, "订单创建失败，库存已补偿，请使用新的请求编号重试");
        }
    }

    public List<Order> list(long userId) {
        return jdbc.query("SELECT * FROM mall_orders WHERE user_id=? ORDER BY id DESC", (row, n) -> row(row), userId)
                .stream().map(this::order).toList();
    }

    public Order pay(long userId, long id) {
        return transactions.execute(status -> {
            lockUser(userId);
            Order order = owned(userId, id);
            if (order.status().equals("CANCELLED")) throw new ApiException(409, "已取消订单不能支付");
            jdbc.update("UPDATE mall_orders SET status='PAID' WHERE id=? AND status='PENDING'", id);
            log.info("Simulated payment orderId={} userId={}", id, userId);
            return owned(userId, id);
        });
    }

    public Order cancel(long userId, long id) {
        OrderRow cancelled = transactions.execute(status -> {
            lockUser(userId);
            OrderRow row = ownedRow(userId, id);
            if (row.status().equals("PAID")) throw new ApiException(409, "已支付订单不能取消");
            jdbc.update("UPDATE mall_orders SET status='CANCELLED' WHERE id=? AND status='PENDING'", id);
            return ownedRow(userId, id);
        });
        // Persist cancellation before releasing: payment cannot race a release, and repeated cancellation retries compensation.
        release(cancelled.reservationKey());
        log.info("Order cancelled and inventory released orderId={} userId={}", id, userId);
        return order(cancelled);
    }

    private void release(String reservationKey) {
        try { products.release(reservationKey, internalKey); }
        catch (RuntimeException failure) {
            log.error("COMPENSATION_REQUIRED reservationKey={}", reservationKey, failure);
            throw new ApiException(503, "库存释放暂未完成，请重试取消；若创建失败，请管理员按日志中的预留编号补偿");
        }
    }

    private void lockUser(long userId) {
        jdbc.update("INSERT INTO cart_users(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=user_id", userId);
        jdbc.queryForObject("SELECT user_id FROM cart_users WHERE user_id=? FOR UPDATE", Long.class, userId);
    }

    private List<ProductClient.Quantity> quantities(long userId) {
        return jdbc.query("SELECT product_id,quantity FROM cart_items WHERE user_id=? ORDER BY product_id",
                (row, n) -> new ProductClient.Quantity(row.getLong("product_id"), row.getInt("quantity")), userId);
    }

    private Order owned(long userId, long id) { return order(ownedRow(userId, id)); }

    private OrderRow ownedRow(long userId, long id) {
        List<OrderRow> rows = jdbc.query("SELECT * FROM mall_orders WHERE user_id=? AND id=?", (row, n) -> row(row), userId, id);
        if (rows.isEmpty()) throw new ApiException(404, "订单不存在");
        return rows.get(0);
    }

    private OrderRow row(java.sql.ResultSet row) throws java.sql.SQLException {
        return new OrderRow(row.getLong("id"), row.getString("order_no"), row.getString("status"), row.getBigDecimal("total"),
                row.getTimestamp("created_at").toLocalDateTime(), row.getString("reservation_key"));
    }

    private Order order(OrderRow row) {
        List<Item> items = jdbc.query("SELECT product_id,name,price,quantity FROM order_items WHERE order_id=? ORDER BY id",
                (line, n) -> item(line.getLong("product_id"), line.getString("name"), line.getBigDecimal("price"), line.getInt("quantity")), row.id());
        return new Order(row.id(), row.orderNo(), row.status(), row.total(), row.createdAt(), items);
    }

    private Item item(long productId, String name, BigDecimal price, int quantity) {
        return new Item(productId, name, price, quantity, price.multiply(BigDecimal.valueOf(quantity)));
    }

    private ApiException productError(FeignException failure) {
        if (failure.status() == 404) return new ApiException(404, "商品不存在");
        if (failure.status() == 409) return new ApiException(409, "库存不足或请求已失效，请刷新购物车后使用新的请求编号重试");
        if (failure.status() == 400) return new ApiException(400, "商品数量或请求编号不合法");
        return new ApiException(503, "商品服务暂不可用，请稍后重试");
    }
}
