package com.example.mall.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.sql.init.mode=always",
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureMockMvc
class OrderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired OrderService service;
    @MockBean UserClient users;
    @MockBean ProductClient products;

    @BeforeEach void externalServiceFixtures() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM mall_orders");
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM cart_users");
        when(users.me(eq("Bearer alice"), anyString())).thenReturn(new UserClient.User(1, "alice"));
        when(users.me(eq("Bearer bob"), anyString())).thenReturn(new UserClient.User(2, "bob"));
        when(products.get(eq(1L), anyString())).thenReturn(new ProductClient.Product(1, "键盘", "机械键盘", "数码",
                new BigDecimal("19.90"), 10, "/keyboard.svg"));
        when(products.reserve(any(), anyString())).thenAnswer(call -> {
            ProductClient.ReserveRequest request = call.getArgument(0);
            int quantity = request.items().get(0).quantity();
            return new ProductClient.Reservation(request.reservationKey(), List.of(new ProductClient.ReservedItem(
                    1, "键盘", new BigDecimal("19.90"), quantity)), new BigDecimal("19.90").multiply(BigDecimal.valueOf(quantity)));
        });
    }

    @Test void cartMoneyOrderIdempotencyAndOwnership() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/cart/items/1").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/items/1").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("total").value(39.8));
        String response = mvc.perform(post("/api/orders").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"requestId\":\"checkout-one\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("total").value(39.8))
                .andExpect(jsonPath("status").value("PENDING")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        mvc.perform(post("/api/orders").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"requestId\":\"checkout-one\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("id").value(id));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mall_orders", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/cart").header("Authorization", "Bearer alice"))
                .andExpect(jsonPath("items").isEmpty()).andExpect(jsonPath("total").value(0));
        mvc.perform(post("/api/orders/" + id + "/pay").header("Authorization", "Bearer bob"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + id + "/pay").header("Authorization", "Bearer alice"))
                .andExpect(jsonPath("status").value("PAID"));
        mvc.perform(post("/api/orders/" + id + "/pay").header("Authorization", "Bearer alice"))
                .andExpect(jsonPath("status").value("PAID"));
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", "Bearer alice"))
                .andExpect(status().isConflict());
        when(products.get(eq(1L), anyString())).thenReturn(new ProductClient.Product(1, "新名称", "", "数码",
                new BigDecimal("99.00"), 10, "/keyboard.svg"));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer alice"))
                .andExpect(jsonPath("$[0].total").value(39.8))
                .andExpect(jsonPath("$[0].items[0].price").value(19.9))
                .andExpect(jsonPath("$[0].items[0].name").value("键盘"));
    }

    @Test void cancellationIsIdempotentAndCancelledOrderCannotBePaid() throws Exception {
        mvc.perform(put("/api/cart/items/1").header("Authorization", "Bearer bob")
                .contentType("application/json").content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String response = mvc.perform(post("/api/orders").header("Authorization", "Bearer bob")
                .contentType("application/json").content("{\"requestId\":\"cancel-case\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", "Bearer bob"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"));
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", "Bearer bob"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"));
        mvc.perform(post("/api/orders/" + id + "/pay").header("Authorization", "Bearer bob"))
                .andExpect(status().isConflict());
    }

    @Test void reservationTimeoutRollsBackOrderKeepsCartAndCompensatesInventory() throws Exception {
        mvc.perform(put("/api/cart/items/1").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"quantity\":2}"))
                .andExpect(status().isOk());
        doThrow(new IllegalStateException("simulated response timeout")).when(products).reserve(any(), anyString());
        mvc.perform(post("/api/orders").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"requestId\":\"timeout-case\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mall_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE user_id=1 AND product_id=1", Integer.class)).isEqualTo(2);
        verify(products).release(startsWith("order-1-"), eq("mall-lab-internal-key-change-me"));
    }

    @Test void cancellationReleaseFailurePersistsCancelledAndRetryFinishesRelease() throws Exception {
        mvc.perform(put("/api/cart/items/1").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        String response = mvc.perform(post("/api/orders").header("Authorization", "Bearer alice")
                .contentType("application/json").content("{\"requestId\":\"release-retry\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        String reservationKey = jdbc.queryForObject("SELECT reservation_key FROM mall_orders WHERE id=?", String.class, id);
        doThrow(new IllegalStateException("simulated inventory outage")).doNothing()
                .when(products).release(eq(reservationKey), anyString());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", "Bearer alice"))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT status FROM mall_orders WHERE id=?", String.class, id)).isEqualTo("CANCELLED");
        mvc.perform(post("/api/orders/" + id + "/pay").header("Authorization", "Bearer alice"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", "Bearer alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"));
    }

    @Test void failedAttemptCompensationCannotReleaseSuccessfulConcurrentRetry() throws Exception {
        service.put(1, 1, 1);
        Map<String, ProductClient.Reservation> activeReservations = new ConcurrentHashMap<>();
        AtomicBoolean first = new AtomicBoolean(true);
        CountDownLatch compensationStarted = new CountDownLatch(1);
        CountDownLatch retryCommitted = new CountDownLatch(1);
        doAnswer(call -> {
            ProductClient.ReserveRequest request = call.getArgument(0);
            ProductClient.Reservation reservation = activeReservations.computeIfAbsent(request.reservationKey(), key ->
                    new ProductClient.Reservation(key, List.of(new ProductClient.ReservedItem(1, "键盘", new BigDecimal("19.90"), 1)), new BigDecimal("19.90")));
            if (first.getAndSet(false)) throw new IllegalStateException("response lost after inventory committed");
            return reservation;
        }).when(products).reserve(any(), anyString());
        doAnswer(call -> {
            compensationStarted.countDown();
            if (!retryCommitted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("retry stalled");
            activeReservations.remove(call.getArgument(0, String.class));
            return null;
        }).when(products).release(anyString(), anyString());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var failingAttempt = executor.submit(() -> {
                try { service.create(1, "concurrent-retry"); }
                catch (RuntimeException expected) { return; }
                throw new AssertionError("First attempt should fail");
            });
            assertThat(compensationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            OrderService.Order retry = service.create(1, "concurrent-retry");
            retryCommitted.countDown();
            failingAttempt.get(10, TimeUnit.SECONDS);
            assertThat(retry.status()).isEqualTo("PENDING");
            assertThat(activeReservations).hasSize(1);
        } finally {
            retryCommitted.countDown();
            executor.shutdownNow();
        }
    }
}
