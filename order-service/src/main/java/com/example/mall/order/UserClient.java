package com.example.mall.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/internal/users/me")
    User me(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Internal-Key") String internalKey);

    record User(long id, String username) {}
}
