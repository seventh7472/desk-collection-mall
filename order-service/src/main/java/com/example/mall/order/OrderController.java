package com.example.mall.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    public record Quantity(@NotNull @Min(1) @Max(99) Integer quantity) {}
    public record Checkout(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String requestId) {}

    @GetMapping("/api/cart")
    public OrderService.Cart cart(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return orders.cart(orders.authenticate(authorization));
    }

    @PutMapping("/api/cart/items/{id}")
    public OrderService.Cart put(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @PathVariable long id, @Valid @RequestBody Quantity request) {
        return orders.put(orders.authenticate(authorization), id, request.quantity());
    }

    @DeleteMapping("/api/cart/items/{id}")
    public OrderService.Cart remove(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable long id) {
        return orders.remove(orders.authenticate(authorization), id);
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderService.Order create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @Valid @RequestBody Checkout request) {
        return orders.create(orders.authenticate(authorization), request.requestId());
    }

    @GetMapping("/api/orders")
    public List<OrderService.Order> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return orders.list(orders.authenticate(authorization));
    }

    @PostMapping("/api/orders/{id}/pay")
    public OrderService.Order pay(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable long id) {
        return orders.pay(orders.authenticate(authorization), id);
    }

    @PostMapping("/api/orders/{id}/cancel")
    public OrderService.Order cancel(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable long id) {
        return orders.cancel(orders.authenticate(authorization), id);
    }
}
