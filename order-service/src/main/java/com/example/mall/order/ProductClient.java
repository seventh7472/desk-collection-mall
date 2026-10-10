package com.example.mall.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/internal/products/{id}")
    Product get(@PathVariable("id") long id, @RequestHeader("X-Internal-Key") String internalKey);

    @PostMapping("/internal/products/reservations")
    Reservation reserve(@RequestBody ReserveRequest request, @RequestHeader("X-Internal-Key") String internalKey);

    @PostMapping("/internal/products/reservations/{key}/release")
    void release(@PathVariable("key") String key, @RequestHeader("X-Internal-Key") String internalKey);

    record Product(long id, String name, String description, String category, BigDecimal price, int stock, String image) {}
    record Quantity(long productId, int quantity) {}
    record ReserveRequest(String reservationKey, List<Quantity> items) {}
    record ReservedItem(long productId, String name, BigDecimal price, int quantity) {}
    record Reservation(String reservationKey, List<ReservedItem> items, BigDecimal total) {}
}
