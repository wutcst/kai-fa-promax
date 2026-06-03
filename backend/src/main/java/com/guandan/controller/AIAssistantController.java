package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.service.AIAssistantService;
import com.guandan.dto.AIChatRequest;
import com.guandan.dto.AISuggestRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "AI出牌建议", description = "根据当前手牌和桌面情况给出出牌建议")
    @PostMapping("/ai/suggest-cards")
    public Result<List<Integer>> suggestCards(@RequestBody AISuggestRequest request) {
        try {
            log.info("收到AI出牌建议请求，手牌数量: {}", request.getHandCards() != null ? request.getHandCards().size() : 0);
            List<Integer> suggestion = aiAssistantService.suggestCards(
                request.getHandCards(),
                request.getLastPlayedCards(),
                request.getLastCardType(),
                request.getLevelCardRank()
            );
            return Result.success(suggestion);
        } catch (Exception e) {
            log.error("AI出牌建议失败: {}", e.getMessage(), e);
            return Result.error("AI出牌建议暂时不可用");
        }
    }

    @Operation(summary = "规则问答", description = "查询掼蛋游戏规则")
    @GetMapping("/ai/rules")
    public Result<String> queryRule(@RequestParam(required = false) String keyword) {
        try {
            log.info("收到规则查询请求: {}", keyword);
            String answer = aiAssistantService.queryRule(keyword);
            return Result.success(answer);
        } catch (Exception e) {
            log.error("规则查询失败: {}", e.getMessage(), e);
            return Result.error("规则查询暂时不可用");
        }
    }
}
