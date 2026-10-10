package com.example.mall.product;

import com.example.mall.common.ApiException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProductServiceTest.Config.class)
class ProductServiceTest {
    @Configuration
    @EnableTransactionManagement
    @Import(ProductService.class)
    static class Config {
        @Bean DataSource dataSource() {
            var dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:product;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return dataSource;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired ProductService service;
    @org.springframework.beans.factory.annotation.Autowired DataSource dataSource;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
    }

    private ReservationRequest request(String key, int quantity) {
        return new ReservationRequest(key, List.of(new ReservationRequest.Item(1L, quantity)));
    }

    @Test void searchesNamesAndFiltersCategory() {
        assertThat(service.list("键盘", null)).extracting(Product::name).containsExactly("机械键盘 K87");
        assertThat(service.list(null, "桌面办公")).hasSize(2);
        assertThat(service.categories()).contains("桌面办公", "数码配件");
    }

    @Test void reservesUsingServerPriceAndReplaysWithoutDoubleDeduction() {
        var first = service.reserve(request("order-1", 3));
        assertThat(first.total()).isEqualByComparingTo("897.00");
        assertThat(service.get(1L).stock()).isEqualTo(37);
        assertThat(service.reserve(request("order-1", 3))).isEqualTo(first);
        assertThat(service.get(1L).stock()).isEqualTo(37);
    }

    @Test void insufficientItemRollsBackEntireReservation() {
        var request = new ReservationRequest("failed", List.of(
                new ReservationRequest.Item(1L, 2), new ReservationRequest.Item(2L, 999)));
        assertThatThrownBy(() -> service.reserve(request)).isInstanceOf(ApiException.class);
        assertThat(service.get(1L).stock()).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class)).isZero();
    }

    @Test void missingProductCannotReserveOrConsumeStock() {
        var request = new ReservationRequest("missing", List.of(
                new ReservationRequest.Item(1L, 2), new ReservationRequest.Item(9999L, 1)));
        assertThatThrownBy(() -> service.reserve(request)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(404));
        assertThat(service.get(1L).stock()).isEqualTo(40);
    }

    @Test void releasesOnlyOnceAndPreventsReuseAfterRelease() {
        service.reserve(request("cancel", 2));
        service.release("cancel");
        service.release("cancel");
        assertThat(service.get(1L).stock()).isEqualTo(40);
        assertThatThrownBy(() -> service.reserve(request("cancel", 2))).isInstanceOf(ApiException.class);
    }

    @Test void rejectsInvalidQuantityAndChangedIdempotencyPayload() {
        assertThatThrownBy(() -> service.reserve(request("zero", 0))).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.reserve(request("negative", -1))).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(400));
        service.reserve(request("same", 1));
        assertThatThrownBy(() -> service.reserve(request("same", 2))).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(409));
        assertThat(service.get(1L).stock()).isEqualTo(39);
    }

    @Test void releaseBeforeReservePreventsLateTimedOutRequestFromConsumingStock() {
        service.release("late");
        service.release("late");
        assertThatThrownBy(() -> service.reserve(request("late", 1))).isInstanceOf(ApiException.class);
        assertThat(service.get(1L).stock()).isEqualTo(40);
    }

    @Test void concurrentSameKeyHasOneStockDeduction() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Reservation>> work = List.of(
                    () -> service.reserve(request("replay", 2)), () -> service.reserve(request("replay", 2)));
            var results = pool.invokeAll(work);
            assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
            assertThat(service.get(1L).stock()).isEqualTo(38);
        } finally { pool.shutdownNow(); }
    }

    @Test void competingReservationsCannotOversell() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> work = List.of(
                    () -> attempt("parallel-a"), () -> attempt("parallel-b"));
            var results = pool.invokeAll(work);
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(service.get(1L).stock()).isEqualTo(10);
        } finally { pool.shutdownNow(); }
    }

    private boolean attempt(String key) {
        try { service.reserve(request(key, 30)); return true; }
        catch (ApiException e) { return false; }
    }
}
