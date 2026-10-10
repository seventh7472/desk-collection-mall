package com.example.mall.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.mall.product", "com.example.mall.common"})
public class ProductApplication {
    public static void main(String[] args) { SpringApplication.run(ProductApplication.class, args); }
}
