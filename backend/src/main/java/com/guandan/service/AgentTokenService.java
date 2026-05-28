package com.guandan.service;

import org.springframework.stereotype.Service;

/**
 * Agent 永久 Token 服务（AI 助手/系统调用专用）。
 * 当前返回默认 agent 身份，后续可配置化。
 */
@Service
public class AgentTokenService {

    public boolean isAgentToken(String token) {
        return token != null && token.startsWith("agent-");
    }

    public Long getAgentUserId() {
        return 0L;
    }

    public String getAgentUsername() {
        return "system-agent";
    }
}
