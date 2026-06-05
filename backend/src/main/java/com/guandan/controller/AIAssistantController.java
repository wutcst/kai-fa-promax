package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.dto.AIChatRequest;
import com.guandan.service.AIAssistantService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIAssistantController {
    private final AIAssistantService service;

    public AIAssistantController(AIAssistantService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody AIChatRequest request) {
        return Result.ok(service.answer(request.getMessage()));
    }
}
// Controller: GET /ai/suggestion and POST /ai/ask
// Fix: handle unsupported question topics in rule QA
// Refactor: rename endpoints to /ai/suggest and /ai/chat
// Docs: /ai/suggest returns {play: [...cards]}, /ai/chat returns {answer: string}
// Test: /ai/ask question classification - card rules, game flow, invalid
// Chore: AIAssistant controller delivery wrap-up
