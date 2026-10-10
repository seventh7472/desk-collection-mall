package com.example.mall.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.mall.common.ApiException;
import feign.FeignException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssistantService {
    private final ProductClient products;
    private final String internalKey;

    public AssistantService(ProductClient products,
            @Value("${INTERNAL_KEY:mall-lab-internal-key-change-me}") String internalKey) {
        this.products = products;
        this.internalKey = internalKey;
    }

    public ChatReply chat(String message) {
        if (message == null || message.isBlank() || message.length() > 500)
            throw new ApiException(400, "请输入 1 至 500 字的问题");
        String text = message.trim();
        if (text.contains("支付") || text.contains("付款"))
            return answer("这是教学用模拟支付，不会扣款。登录后在订单列表中，对待支付订单点击“模拟支付”。实际状态请以订单列表为准。");
        if (text.contains("取消") || text.contains("退款"))
            return answer("待支付订单可在订单列表中取消，取消后释放库存。已支付订单不能在本实验中取消；本项目未接入真实支付及退款。");
        if (text.contains("订单") && !text.contains("下单"))
            return answer("请登录后查看订单列表，那里显示你的订单和状态。我是商品检索与规则问答助手，不能查询或修改个人订单，也不会推断付款结果。");
        if (text.contains("登录") || text.contains("注册"))
            return answer("先注册一个账户，再使用用户名和密码登录。登录后可以使用购物车和订单功能。请勿在聊天中发送密码。");
        if (text.contains("购物车") || text.contains("购物") || text.contains("下单"))
            return answer("购物流程：注册并登录 → 挑选商品加入购物车 → 调整数量 → 创建订单 → 在订单列表中模拟支付。价格和库存由服务端校验。");

        String keyword = null;
        for (String candidate : List.of("键盘", "鼠标", "显示器", "扩展坞", "耳机", "硬盘")) {
            if (text.contains(candidate)) { keyword = candidate; break; }
        }
        if (keyword == null && !(text.contains("推荐") || text.contains("商品") || text.contains("库存") || text.contains("有哪些")))
            keyword = text.replaceFirst("^(查找|搜索|想买|我要买)", "").trim();
        List<Product> matches;
        try { matches = products.search(keyword, internalKey); }
        catch (FeignException e) { throw new ApiException(503, "商品服务暂不可用，请稍后重试"); }
        if (matches.isEmpty())
            return answer("未找到匹配商品。可以试试键盘、鼠标、显示器、耳机等关键词。我是商品检索与规则问答助手，未连接外部大语言模型。");
        String details = matches.stream().map(p -> p.name() + "：¥" + p.price().toPlainString() + "，当前库存 " + p.stock())
                .collect(Collectors.joining("；"));
        return new ChatReply("根据商品服务实时检索：" + details + "。库存可能变化，以提交订单时校验为准。", matches);
    }

    private ChatReply answer(String text) { return new ChatReply(text, List.of()); }
}
