package com.guandan.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类（使用@ServerEndpoint注解方式）
 * 负责人：成员B（通讯与架构）
 *
 * <p>启用WebSocket支持，用于扫描@ServerEndpoint注解。
 * Spring Boot需要这个Bean来识别并注册所有带有@ServerEndpoint注解的端点类。
 */
@Configuration
public class GameWebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
