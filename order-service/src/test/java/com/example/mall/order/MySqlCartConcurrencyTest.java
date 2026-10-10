package com.example.mall.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Opt-in test: uses the lab MySQL schema and cleans up only its own random user. */
@EnabledIfEnvironmentVariable(named = "MALL_MYSQL_TEST_URL", matches = ".+")
class MySqlCartConcurrencyTest {
    @Test void concurrentCartUpdatesSerializeWithoutMysqlLockUpgradeDeadlock() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("MALL_MYSQL_TEST_URL"), "mall", "mall_lab_password");
        var jdbc = new JdbcTemplate(source);
        var products = mock(ProductClient.class);
        when(products.get(eq(1L), anyString())).thenReturn(new ProductClient.Product(1, "键盘", "机械键盘", "数码",
                new BigDecimal("19.90"), 99, "/keyboard.svg"));
        var service = new OrderService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)),
                mock(UserClient.class), products, "mall-lab-internal-key-change-me");
        long userId = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());
        service.put(userId, 1, 1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 10; round++) {
                var start = new CyclicBarrier(8);
                var operations = new ArrayList<Future<OrderService.Cart>>();
                for (int i = 1; i <= 8; i++) {
                    int quantity = i;
                    operations.add(executor.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return service.put(userId, 1, quantity);
                    }));
                }
                for (var operation : operations) assertThat(operation.get(20, TimeUnit.SECONDS).items()).hasSize(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE user_id=?", Integer.class, userId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE user_id=?", Integer.class, userId)).isBetween(1, 8);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(25, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM cart_items WHERE user_id=?", userId);
            jdbc.update("DELETE FROM cart_users WHERE user_id=?", userId);
        }
    }
}
