package com.example.mall.assistant;

import java.util.List;

public record ChatReply(String reply, List<Product> products) { }
