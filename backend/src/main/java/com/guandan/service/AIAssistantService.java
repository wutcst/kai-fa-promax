package com.guandan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AIAssistantService {

    private static final String LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String API_KEY = "sk-ebqmsclwuuqtgoouaffxlzfytyvqkawouxewyrcghhyopyzo";
    private static final String LLM_MODEL = "Qwen/Qwen2.5-7B-Instruct";

    private static final int MAX_CARDS_PER_HAND = 27;
    private static final int LEVEL_CARD_RANK_MIN = 0;
    private static final int LEVEL_CARD_RANK_MAX = 12;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<ChatMessage> conversationHistory = new ArrayList<>();

    // ==================== 公开方法 ====================

    /**
     * 向AI助手提问
     */
    public String chat(String userMessage) {
        if (isBlank(userMessage)) {
            return "请输入您的问题";
        }
        try {
            List<ChatMessage> messages = buildChatMessages(userMessage);
            JsonNode requestBody = buildLlmRequestBody(messages);
            log.info("发送LLM API请求: {}", requestBody);

            String responseBody = executeLlmRequest(requestBody);
            return parseLlmResponse(responseBody);
        } catch (Exception e) {
            log.error("AI服务异常", e);
            throw new RuntimeException("AI服务暂时不可用: " + e.getMessage());
        }
    }

    /**
     * AI出牌建议
     */
    public List<Integer> suggestCards(List<Integer> handCards, List<Integer> lastPlayedCards,
                                       String lastCardType, int levelCardRank) {
        handCards = sanitizeHandCards(handCards);
        if (handCards.isEmpty()) {
            return new ArrayList<>();
        }

        levelCardRank = clampLevelCardRank(levelCardRank);

        if (isFreePlay(lastPlayedCards)) {
            return suggestSmallestCard(handCards);
        }

        int lastCardValue = resolveLastCardValue(lastPlayedCards);
        if (lastCardValue < 0) {
            return new ArrayList<>();
        }

        Integer bestCard = findBestFollowCard(handCards, lastCardValue);
        if (bestCard != null) {
            List<Integer> result = new ArrayList<>();
            result.add(bestCard);
            log.info("AI出牌建议（跟牌）：推荐单张 {}", bestCard);
            return result;
        }

        log.info("AI出牌建议：没有合适的牌可出");
        return new ArrayList<>();
    }

    /**
     * 规则问答
     */
    public String queryRule(String keyword) {
        if (isBlank(keyword)) {
            return buildDefaultRuleDescription();
        }
        String kw = keyword.trim();
        if (containsKeyword(kw, "炸弹", "炸")) {
            return "炸弹：四张或四张以上相同点数的牌，可以管任何非炸弹牌型。炸弹之间按点数大小比较。";
        }
        if (containsKeyword(kw, "顺子")) {
            return "顺子：五张或五张以上连续点数的牌（如3-4-5-6-7），不能包含大小王。";
        }
        if (containsKeyword(kw, "同花顺")) {
            return "同花顺：五张相同花色且点数连续的牌，是最大的非炸弹牌型。可以管任何非炸弹牌型。";
        }
        if (containsKeyword(kw, "级牌", "升级")) {
            return "级牌：每局游戏中指定一个点数作为级牌，打到相应级数的队伍获胜。级牌的大小仅次于大小王。";
        }
        if (containsKeyword(kw, "头游", "二游", "三游", "末游")) {
            return "排名：四人按出完牌的顺序依次为头游、二游、三游、末游。头游所在队伍得分升级。";
        }
        if (containsKeyword(kw, "三带二")) {
            return "三带二：三张相同点数的牌加两张相同点数的牌（三张和对子点数不同）。";
        }
        if (containsKeyword(kw, "钢板")) {
            return "钢板：两组连续的三张（如333444），共6张牌。";
        }
        return buildGenericRuleDescription(keyword);
    }

    // ==================== LLM 交互相关私有方法 ====================

    private List<ChatMessage> buildChatMessages(String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system",
            "你是掼蛋游戏的智能助手。你的任务是：\n" +
            "1. 解释掼蛋游戏规则\n" +
            "2. 提供打牌策略建议\n" +
            "3. 解答玩家关于游戏的问题\n" +
            "4. 友好、专业地与玩家交流\n\n" +
            "请用简洁、易懂的语言回答问题。"
        ));
        messages.add(new ChatMessage("user", userMessage));
        return messages;
    }

    private JsonNode buildLlmRequestBody(List<ChatMessage> messages) {
        return objectMapper.createObjectNode()
            .put("model", LLM_MODEL)
            .set("messages", objectMapper.valueToTree(messages));
    }

    private String executeLlmRequest(JsonNode requestBody) throws Exception {
        RequestBody body = RequestBody.create(
            requestBody.toString(),
            MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
            .url(LLM_API_URL)
            .post(body)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("LLM API调用失败: {}", response.code());
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("错误详情: {}", errorBody);
                throw new RuntimeException("API调用失败: " + response.code());
            }
            return response.body().string();
        }
    }

    private String parseLlmResponse(String responseBody) throws Exception {
        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        String aiResponse = jsonResponse
            .path("choices")
            .get(0)
            .path("message")
            .path("content")
            .asText();
        log.info("AI回答: {}", aiResponse);
        return aiResponse;
    }

    // ==================== 出牌建议相关私有方法 ====================

    private List<Integer> sanitizeHandCards(List<Integer> handCards) {
        if (handCards == null || handCards.isEmpty()) {
            log.warn("出牌建议：手牌为空");
            return new ArrayList<>();
        }
        List<Integer> distinct = handCards.stream().distinct().toList();
        if (distinct.size() != handCards.size()) {
            log.warn("出牌建议：手牌包含重复ID，已自动去重");
            return new ArrayList<>(distinct);
        }
        return handCards;
    }

    private int clampLevelCardRank(int levelCardRank) {
        if (levelCardRank < LEVEL_CARD_RANK_MIN || levelCardRank > LEVEL_CARD_RANK_MAX) {
            log.warn("出牌建议：级牌参数异常 {}，已重置为0", levelCardRank);
            return 0;
        }
        return levelCardRank;
    }

    private boolean isFreePlay(List<Integer> lastPlayedCards) {
        return lastPlayedCards == null || lastPlayedCards.isEmpty();
    }

    private List<Integer> suggestSmallestCard(List<Integer> handCards) {
        List<Integer> sorted = new ArrayList<>(handCards);
        sorted.sort(Integer::compareTo);
        List<Integer> result = new ArrayList<>();
        result.add(sorted.get(0));
        log.info("出牌建议（自由出牌）：推荐单张 {}", sorted.get(0));
        return result;
    }

    private int resolveLastCardValue(List<Integer> lastPlayedCards) {
        if (lastPlayedCards == null || lastPlayedCards.isEmpty()) {
            return -1;
        }
        return lastPlayedCards.stream().max(Integer::compareTo).orElse(-1);
    }

    private Integer findBestFollowCard(List<Integer> handCards, int minValue) {
        Integer bestCard = null;
        for (Integer cardId : handCards) {
            if (cardId > minValue) {
                if (bestCard == null || cardId < bestCard) {
                    bestCard = cardId;
                }
            }
        }
        return bestCard;
    }

    // ==================== 规则问答相关私有方法 ====================

    private boolean containsKeyword(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String buildDefaultRuleDescription() {
        return "掼蛋是一种流行于江苏、安徽等地的扑克游戏。\n" +
               "基本规则：四人游戏，两两组队，使用两副扑克共108张牌。\n" +
               "按照出完牌的先后顺序分为头游、二游、三游、末游。\n" +
               "级牌：每局有一个级牌，打到相应级数的队伍获胜。";
    }

    private String buildGenericRuleDescription(String keyword) {
        return "关于'" + keyword + "'的规则说明：\n" +
               "掼蛋使用两副扑克共108张牌，四人游戏，两两组队。\n" +
               "牌型包括：单张、对子、三张、顺子（5+张）、三带二、钢板、同花顺、炸弹等。\n" +
               "如需更详细说明，请提供具体关键词。";
    }

    // ==================== 工具方法 ====================

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    // ==================== 内部类 ====================

    private static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
