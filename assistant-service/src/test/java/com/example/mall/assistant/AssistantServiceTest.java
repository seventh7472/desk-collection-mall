package com.example.mall.assistant;

import com.example.mall.common.ApiException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AssistantServiceTest {
    private final Product keyboard = new Product(1L, "机械键盘 K87", "办公键盘", "桌面办公",
            new BigDecimal("299.00"), 40, "/images/keyboard.svg");
    private final AssistantService service = new AssistantService((q, key) ->
            q == null || q.equals("键盘") ? List.of(keyboard) : List.of(), "test-key");

    @Test void findsProductsAndReportsActualPriceAndStock() {
        var reply = service.chat("想买键盘，有库存吗");
        assertThat(reply.products()).containsExactly(keyboard);
        assertThat(reply.reply()).contains("299.00", "40");
    }

    @Test void describesSimulationWithoutInventingPaymentResult() {
        var reply = service.chat("如何支付订单");
        assertThat(reply.products()).isEmpty();
        assertThat(reply.reply()).contains("模拟支付", "不会扣款");
        assertThat(reply.reply()).doesNotContain("支付成功");
    }

    @Test void explainsShoppingAndRefusesToInventOrderState() {
        assertThat(service.chat("如何购物").reply()).contains("购物车", "登录");
        assertThat(service.chat("我的订单现在是什么状态").reply()).contains("订单列表");
    }

    @Test void missingMatchesGivesClearSearchGuidance() {
        assertThat(service.chat("查找打印机").reply()).contains("未找到", "规则");
        assertThat(service.chat("查找打印机").products()).isEmpty();
    }

    @Test void rejectsBlankOrOversizedInput() {
        assertThatThrownBy(() -> service.chat(" ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.chat("a".repeat(501))).isInstanceOf(ApiException.class);
    }
}
