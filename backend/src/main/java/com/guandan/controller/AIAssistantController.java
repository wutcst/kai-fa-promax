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

/**
 * AI助手控制器
 * 提供AI聊天、出牌建议、规则问答等智能辅助功能。
 * 所有接口统一返回 Result<T> 格式。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>POST /api/ai/chat — AI聊天，输入问题返回回答</li>
 *   <li>POST /api/ai/suggest-cards — AI出牌建议，根据手牌和桌⾯情况推荐出牌</li>
 *   <li>GET /api/ai/rules — 规则问答，查询掼蛋游戏规则</li>
 * </ul>
 *
 * <h3>异常场景说明</h3>
 * <ul>
 *   <li>参数为空或格式异常 → 返回 Result.error("请输入您的问题")</li>
 *   <li>消息长度超过500字 → 返回 Result.error("请输入您的问题")</li>
 *   <li>手牌数据为空 → 返回 Result.error("手牌数据不能为空")</li>
 *   <li>手牌数量超过27张 → 返回 Result.error("手牌数据异常")</li>
 *   <li>服务内部异常 → 返回 Result.error("AI助手暂时无法回答，请稍后再试")</li>
 *   <li>出牌建议不可用 → 返回 Result.error("AI出牌建议暂时不可用")</li>
 *   <li>规则查询失败 → 返回 Result.error("规则查询暂时不可用")</li>
 *   <li>未找到相关规则 → 返回 Result.error("未找到相关规则说明")</li>
 * </ul>
 *
 * <h3>回归验证点</h3>
 * <ul>
 *   <li>[TC-AI-CHAT-001] POST /api/ai/chat 传入空消息 → 返回 Result.error("请输入您的问题")</li>
 *   <li>[TC-AI-CHAT-002] POST /api/ai/chat 传入超长消息(>500字) → 返回 Result.error("请输入您的问题")</li>
 *   <li>[TC-AI-CHAT-003] POST /api/ai/chat 正常消息 → 返回 Result.success 包含 AI 回答</li>
 *   <li>[TC-AI-SUGGEST-001] POST /api/ai/suggest-cards 传入空手牌 → 返回 Result.error("手牌数据不能为空")</li>
 *   <li>[TC-AI-SUGGEST-002] POST /api/ai/suggest-cards 手牌超过27张 → 返回 Result.error("手牌数据异常")</li>
 *   <li>[TC-AI-SUGGEST-003] POST /api/ai/suggest-cards 正常请求 → 返回 Result.success 包含出牌建议列表</li>
 *   <li>[TC-AI-RULE-001] GET /api/ai/rules 空关键词 → 正常返回规则列表或空结果</li>
 *   <li>[TC-AI-RULE-002] GET /api/ai/rules 关键词超长(>100字) → 返回 Result.error("关键词太长，请精简后重试")</li>
 *   <li>[TC-AI-RULE-003] GET /api/ai/rules 有效关键词 → 返回 Result.success 包含规则说明</li>
 *   <li>[TC-AI-RULE-004] GET /api/ai/rules 不存在的关键词 → 返回 Result.error("未找到相关规则说明")</li>
 * </ul>
 *
 * @author kai-fa-promax 开发团队
 */

@Slf4j
@Tag(name = "AI助手", description = "AI智能助手功能")
@RestController
@RequestMapping("/api")
public class AIAssistantController {

    @Autowired
    private AIAssistantService aiAssistantService;

    /**
     * AI聊天 - 向AI助手提问，获取回答
     */
    @Operation(summary = "AI聊天", description = "向AI助手提问，获取回答")
    @PostMapping("/ai/chat")
    public Result<String> chat(@RequestBody AIChatRequest request) {
        String validatedMessage = validateChatRequest(request);
        if (validatedMessage == null) {
            return Result.error("请输入您的问题");
        }
        try {
            log.info("收到AI聊天请求: {}", validatedMessage);
            String response = aiAssistantService.chat(validatedMessage);
            return Result.success(response);
        } catch (Exception e) {
            log.error("AI聊天失败: {}", e.getMessage(), e);
            return Result.error("AI助手暂时无法回答，请稍后再试");
        }
    }

    /**
     * AI出牌建议 - 根据当前手牌和桌面情况给出出牌建议
     */
    @Operation(summary = "AI出牌建议", description = "根据当前手牌和桌面情况给出出牌建议")
    @PostMapping("/ai/suggest-cards")
    public Result<List<Integer>> suggestCards(@RequestBody AISuggestRequest request) {
        String validationError = validateSuggestRequest(request);
        if (validationError != null) {
            return Result.error(validationError);
        }
        try {
            log.info("收到AI出牌建议请求，手牌数量: {}", request.getHandCards().size());
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

    /**
     * 规则问答 - 查询掼蛋游戏规则
     */
    @Operation(summary = "规则问答", description = "查询掼蛋游戏规则")
    @GetMapping("/ai/rules")
    public Result<String> queryRule(@RequestParam(required = false) String keyword) {
        if (isKeywordTooLong(keyword)) {
            return Result.error("关键词太长，请精简后重试");
        }
        try {
            log.info("收到规则查询请求: {}", keyword);
            String answer = aiAssistantService.queryRule(keyword);
            if (isEmptyResult(answer)) {
                return Result.error("未找到相关规则说明");
            }
            return Result.success(answer);
        } catch (Exception e) {
            log.error("规则查询失败: {}", e.getMessage(), e);
            return Result.error("规则查询暂时不可用");
        }
    }

    // ========== 参数校验私有方法 ==========

    /**
     * 校验AI聊天请求参数
     * @return 通过校验的消息文本，校验失败返回null
     */
    private String validateChatRequest(AIChatRequest request) {
        if (request == null) {
            return null;
        }
        String message = request.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.length() > 500) {
            return null;
        }
        return trimmed;
    }

    /**
     * 校验出牌建议请求参数
     * @return 校验失败时的错误信息，校验通过返回null
     */
    private String validateSuggestRequest(AISuggestRequest request) {
        if (request == null || request.getHandCards() == null) {
            return "手牌数据不能为空";
        }
        if (request.getHandCards().isEmpty()) {
            return "手牌数据不能为空";
        }
        if (request.getHandCards().size() > 27) {
            log.warn("AI出牌建议：手牌数量异常 {}", request.getHandCards().size());
            return "手牌数据异常";
        }
        return null;
    }

    /**
     * 判断关键词是否超长
     */
    private boolean isKeywordTooLong(String keyword) {
        return keyword != null && keyword.length() > 100;
    }

    /**
     * 判断结果是否为空
     */
    private boolean isEmptyResult(String result) {
        return result == null || result.trim().isEmpty();
    }
}
