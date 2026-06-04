package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.service.AIAssistantService;
import com.guandan.dto.AIChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "AI助手", description = "AI智能助手功能")
@RestController
@RequestMapping("/api")
public class AIAssistantController {

    @Autowired
    private AIAssistantService aiAssistantService;

    @Operation(summary = "AI聊天", description = "向AI助手提问，获取回答")
    @PostMapping("/ai/chat")
    public Result<String> chat(@RequestBody AIChatRequest request) {
        try {
            log.info("收到AI聊天请求: {}", request.getMessage());
            String response = aiAssistantService.chat(request.getMessage());
            return Result.success(response);
        } catch (Exception e) {
            log.error("AI聊天失败: {}", e.getMessage(), e);
            return Result.error("AI助手暂时无法回答，请稍后再试");
        }
    }
}
