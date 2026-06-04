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
