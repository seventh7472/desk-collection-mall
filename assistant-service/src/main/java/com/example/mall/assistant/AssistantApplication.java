package com.example.mall.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.example.mall.assistant", "com.example.mall.common"})
@EnableFeignClients
public class AssistantApplication {
    public static void main(String[] args) { SpringApplication.run(AssistantApplication.class, args); }
}
