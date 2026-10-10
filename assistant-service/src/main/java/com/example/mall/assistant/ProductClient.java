package com.example.mall.assistant;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/internal/products")
    List<Product> search(@RequestParam(value = "q", required = false) String q,
                         @RequestHeader("X-Internal-Key") String internalKey);
}
