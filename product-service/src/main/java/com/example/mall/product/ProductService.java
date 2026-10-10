package com.example.mall.product;

import com.example.mall.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final RowMapper<Product> PRODUCT = (rs, row) -> new Product(rs.getLong("id"),
            rs.getString("name"), rs.getString("description"), rs.getString("category"),
            rs.getBigDecimal("price"), rs.getInt("stock"), rs.getString("image"));
    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Product> list(String q, String category) {
        String query = q == null ? "" : q.trim();
        if (query.length() > 100 || category != null && category.length() > 50)
            throw new ApiException(400, "搜索条件过长");
        String categoryValue = category == null ? "" : category.trim();
        return jdbc.query("SELECT * FROM products WHERE (LOWER(name) LIKE ? OR LOWER(description) LIKE ?) "
                        + "AND (? = '' OR category = ?) ORDER BY id", PRODUCT,
                "%" + query.toLowerCase(java.util.Locale.ROOT) + "%",
                "%" + query.toLowerCase(java.util.Locale.ROOT) + "%", categoryValue, categoryValue);
    }

    public Product get(Long id) { return find(id, false); }

    private Product find(Long id, boolean lock) {
        if (id == null || id <= 0) throw new ApiException(400, "商品编号无效");
        var found = jdbc.query("SELECT * FROM products WHERE id = ?" + (lock ? " FOR UPDATE" : ""), PRODUCT, id);
        if (found.isEmpty()) throw new ApiException(404, "商品不存在");
        return found.get(0);
    }

    public List<String> categories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM products ORDER BY category", String.class);
    }

    @Transactional
    public Reservation reserve(ReservationRequest request) {
        if (request == null) throw new ApiException(400, "预留请求不能为空");
        validateKey(request.reservationKey());
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 50)
            throw new ApiException(400, "预留商品须为 1 至 50 项");
        var quantities = new TreeMap<Long, Integer>();
        for (var item : request.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0
                    || item.quantity() == null || item.quantity() <= 0 || item.quantity() > 999)
                throw new ApiException(400, "商品编号或数量无效，数量须为 1 至 999");
            if (quantities.putIfAbsent(item.productId(), item.quantity()) != null)
                throw new ApiException(400, "预留请求不能包含重复商品");
        }
        String key = request.reservationKey();
        String fingerprint = quantities.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
        // A duplicate-key update takes an exclusive lock; INSERT IGNORE would deadlock on lock upgrades.
        jdbc.update("INSERT INTO reservations(reservation_key,request_fingerprint,status,total) "
                + "VALUES (?,?,'PENDING',0) ON DUPLICATE KEY UPDATE reservation_key=reservation_key", key, fingerprint);
        var state = lockReservation(key);
        if ("RELEASED".equals(state.status())) throw new ApiException(409, "该预留请求已取消");
        if (!fingerprint.equals(state.fingerprint())) throw new ApiException(409, "预留编号已用于不同商品或数量");
        if ("RESERVED".equals(state.status())) return readReservation(key, state.total());

        var items = new ArrayList<Reservation.Item>();
        BigDecimal total = new BigDecimal("0.00");
        for (var entry : quantities.entrySet()) {
            var product = find(entry.getKey(), true);
            if (product.stock() < entry.getValue()) throw new ApiException(409, product.name() + "库存不足");
            jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ?", entry.getValue(), product.id());
            jdbc.update("INSERT INTO reservation_items(reservation_key,product_id,name,price,quantity) VALUES (?,?,?,?,?)",
                    key, product.id(), product.name(), product.price(), entry.getValue());
            items.add(new Reservation.Item(product.id(), product.name(), product.price(), entry.getValue()));
            total = total.add(product.price().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        jdbc.update("UPDATE reservations SET status='RESERVED', total=? WHERE reservation_key=?", total, key);
        log.info("Inventory reserved key={} itemCount={} total={}", key, items.size(), total);
        return new Reservation(key, items, total);
    }

    @Transactional
    public void release(String key) {
        validateKey(key);
        // Cancellation tombstones also prevent a timed-out reserve from arriving after compensation.
        jdbc.update("INSERT INTO reservations(reservation_key,request_fingerprint,status,total) "
                + "VALUES (?,'','RELEASED',0) ON DUPLICATE KEY UPDATE reservation_key=reservation_key", key);
        var state = lockReservation(key);
        if ("RELEASED".equals(state.status())) return;
        var reservation = readReservation(key, state.total());
        for (var item : reservation.items()) {
            find(item.productId(), true);
            jdbc.update("UPDATE products SET stock = stock + ? WHERE id = ?", item.quantity(), item.productId());
        }
        jdbc.update("UPDATE reservations SET status='RELEASED' WHERE reservation_key=?", key);
        log.info("Inventory released key={}", key);
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,128}"))
            throw new ApiException(400, "预留编号须为 1 至 128 位字母、数字、下划线或连字符");
    }

    private State lockReservation(String key) {
        return jdbc.queryForObject("SELECT request_fingerprint,status,total FROM reservations WHERE reservation_key=? FOR UPDATE",
                (rs, row) -> new State(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)), key);
    }

    private Reservation readReservation(String key, BigDecimal total) {
        var items = jdbc.query("SELECT product_id,name,price,quantity FROM reservation_items WHERE reservation_key=? ORDER BY product_id",
                (rs, row) -> new Reservation.Item(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4)), key);
        return new Reservation(key, items, total);
    }

    private record State(String fingerprint, String status, BigDecimal total) { }
}
