package com.example.mall.product;

import java.math.BigDecimal;
import java.util.List;

public record Reservation(String reservationKey, List<Item> items, BigDecimal total) {
    public record Item(Long productId, String name, BigDecimal price, int quantity) { }
}
