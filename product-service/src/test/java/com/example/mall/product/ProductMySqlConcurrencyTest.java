package com.example.mall.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MYSQL_CONCURRENCY_TEST", matches = "true")
class ProductMySqlConcurrencyTest {
    @Test void fourConcurrentSameKeyRequestsAllReplayAndReleaseExactlyOnce() throws Exception {
        var dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("MYSQL_TEST_URL", "jdbc:mysql://localhost:13306/mall_product?allowPublicKeyRetrieval=true&useSSL=false"),
                System.getenv().getOrDefault("DB_USER", "mall"),
                System.getenv().getOrDefault("DB_PASSWORD", "mall_lab_password"));
        var jdbc = new JdbcTemplate(dataSource);
        var service = new ProductService(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var pool = Executors.newFixedThreadPool(4);
        int initialStock = service.get(1L).stock();
        try {
            for (int iteration = 0; iteration < 20; iteration++) {
                String key = "mysql-test-" + UUID.randomUUID();
                var request = new ReservationRequest(key, List.of(new ReservationRequest.Item(1L, 1)));
                var start = new CyclicBarrier(4);
                Callable<Reservation> reserve = () -> {
                    start.await();
                    return tx.execute(status -> service.reserve(request));
                };
                try {
                    var results = pool.invokeAll(List.of(reserve, reserve, reserve, reserve));
                    for (var result : results) assertThat(result.get().total()).isEqualByComparingTo("299.00");
                    assertThat(service.get(1L).stock()).isEqualTo(initialStock - 1);
                } finally {
                    Callable<Void> release = () -> { tx.executeWithoutResult(status -> service.release(key)); return null; };
                    for (Future<Void> result : pool.invokeAll(List.of(release, release, release, release))) result.get();
                }
                assertThat(service.get(1L).stock()).isEqualTo(initialStock);
            }
        } finally { pool.shutdownNow(); }
    }
}
