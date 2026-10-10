package com.example.mall.product;

import java.util.List;

public record ReservationRequest(String reservationKey, List<Item> items) {
    public record Item(Long productId, Integer quantity) { }
}
