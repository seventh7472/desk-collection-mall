package com.example.mall.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);
    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) { this.assistant = assistant; }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest request) {
        var reply = assistant.chat(request.message());
        log.info("Assistant answered productCount={}", reply.products().size());
        return reply;
    }

    public record ChatRequest(String message) { }
}
