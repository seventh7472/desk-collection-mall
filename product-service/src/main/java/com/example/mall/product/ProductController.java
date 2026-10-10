package com.example.mall.product;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.example.mall.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ProductController {
    private static final String LIST_RESOURCE = "public-product-list";
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @PostConstruct
    void configureLimit() {
        var rule = new FlowRule(LIST_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(20);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @GetMapping("/api/products")
    public List<Product> list(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String category) {
        try (Entry ignored = SphU.entry(LIST_RESOURCE)) {
            return products.list(q, category);
        } catch (BlockException blocked) {
            throw new ApiException(429, "商品查询过于频繁，请稍后重试");
        }
    }

    @GetMapping({"/api/products/{id}", "/internal/products/{id}"})
    public Product get(@PathVariable Long id) { return products.get(id); }

    @GetMapping("/api/categories")
    public List<String> categories() { return products.categories(); }

    @GetMapping("/internal/products")
    public List<Product> internalList(@RequestParam(required = false) String q) { return products.list(q, null); }

    @PostMapping("/internal/products/reservations")
    public Reservation reserve(@RequestBody ReservationRequest request) { return products.reserve(request); }

    @PostMapping("/internal/products/reservations/{key}/release")
    public java.util.Map<String, String> release(@PathVariable String key) {
        products.release(key);
        return java.util.Map.of("reservationKey", key, "status", "RELEASED");
    }
}
