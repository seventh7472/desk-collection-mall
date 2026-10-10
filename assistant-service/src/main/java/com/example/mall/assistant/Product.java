package com.example.mall.assistant;

import java.math.BigDecimal;

public record Product(Long id, String name, String description, String category,
                      BigDecimal price, int stock, String image) { }
