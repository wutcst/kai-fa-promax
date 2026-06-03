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

private static final String GLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String API_KEY = "sk-ebqmsclwuuqtgoouaffxlzfytyvqkawouxewyrcghhyopyzo";
    private static final String MODEL = "Qwen/Qwen2.5-7B-Instruct"; // 使用Qwen2.5对话模型

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 对话历史（可选，用于上下文）
    private List<ChatMessage> conversationHistory = new ArrayList<>();

    public String chat(String userMessage) {
        try {
            // 添加系统提示词
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system",
                "你是掼蛋游戏的智能助手。你的任务是：\n" +
                "1. 解释掼蛋游戏规则\n" +
                "2. 提供打牌策略建议\n" +
                "3. 解答玩家关于游戏的问题\n" +
                "4. 友好、专业地与玩家交流\n\n" +
                "请用简洁、易懂的语言回答问题。"
            ));

            // 添加用户消息
            messages.add(new ChatMessage("user", userMessage));

            // 构建请求体
            JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", MODEL)
                .set("messages", objectMapper.valueToTree(messages));

            log.info("发送GLM API请求: {}", requestBody);

            // 创建HTTP请求
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(GLM_API_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

            // 发送请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("GLM API调用失败: {}", response.code());
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("错误详情: {}", errorBody);
                    throw new RuntimeException("API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                log.info("GLM API响应: {}", responseBody);

                // 解析响应
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

        } catch (Exception e) {
            log.error("AI服务异常", e);
            throw new RuntimeException("AI服务暂时不可用: " + e.getMessage());
        }
    }

    /**
     * AI出牌建议
     * @param handCards 当前手牌ID列表
     * @param lastPlayedCards 上一手打出的牌ID列表
     * @param lastCardType 上一手牌的类型
     * @param levelCardRank 级牌点数
     * @return 建议打出的牌ID列表
     */
    public List<Integer> suggestCards(List<Integer> handCards, List<Integer> lastPlayedCards,
                                       String lastCardType, int levelCardRank) {
        if (handCards == null || handCards.isEmpty()) {
            log.warn("AI出牌建议：手牌为空");
            return new ArrayList<>();
        }
        // 如果上一手是空或者自由出牌，选择最小的牌打出
        if (lastPlayedCards == null || lastPlayedCards.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(handCards);
            sorted.sort(Integer::compareTo);
            List<Integer> result = new ArrayList<>();
            result.add(sorted.get(0));
            log.info("AI出牌建议（自由出牌）：推荐单张 {}", sorted.get(0));
            return result;
        }
        // 跟牌场景：找一个比上一手大的最小牌
        int lastCardValue = lastPlayedCards.get(lastPlayedCards.size() - 1);
        Integer bestCard = null;
        for (Integer cardId : handCards) {
            if (cardId > lastCardValue) {
                if (bestCard == null || cardId < bestCard) {
                    bestCard = cardId;
                }
            }
        }
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
     * @param keyword 查询关键词
     * @return 规则说明文字
     */
    public String queryRule(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return "掼蛋是一种流行于江苏、安徽等地的扑克游戏。\n" +
                   "基本规则：四人游戏，两两组队，使用两副扑克共108张牌。\n" +
                   "按照出完牌的先后顺序分为头游、二游、三游、末游。\n" +
                   "级牌：每局有一个级牌，打到相应级数的队伍获胜。";
        }
        // 关键词匹配
        String kw = keyword.trim();
        if (kw.contains("炸弹") || kw.contains("炸")) {
            return "炸弹：四张或四张以上相同点数的牌，可以管任何非炸弹牌型。炸弹之间按点数大小比较。";
        }
        if (kw.contains("顺子")) {
            return "顺子：五张或五张以上连续点数的牌（如3-4-5-6-7），不能包含大小王。";
        }
        if (kw.contains("同花顺")) {
            return "同花顺：五张相同花色且点数连续的牌，是最大的非炸弹牌型。可以管任何非炸弹牌型。";
        }
        if (kw.contains("级牌") || kw.contains("升级")) {
            return "级牌：每局游戏中指定一个点数作为级牌，打到相应级数的队伍获胜。级牌的大小仅次于大小王。";
        }
        if (kw.contains("头游") || kw.contains("二游") || kw.contains("三游") || kw.contains("末游")) {
            return "排名：四人按出完牌的顺序依次为头游、二游、三游、末游。头游所在队伍得分升级。";
        }
        if (kw.contains("三带二")) {
            return "三带二：三张相同点数的牌加两张相同点数的牌（三张和对子点数不同）。";
        }
        if (kw.contains("钢板")) {
            return "钢板：两组连续的三张（如333444），共6张牌。";
        }
        return "关于'" + keyword + "'的规则说明：\n" +
               "掼蛋使用两副扑克共108张牌，四人游戏，两两组队。\n" +
               "牌型包括：单张、对子、三张、顺子（5+张）、三带二、钢板、同花顺、炸弹等。\n" +
               "如需更详细说明，请提供具体关键词。";
    }

    // 聊天消息类
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
