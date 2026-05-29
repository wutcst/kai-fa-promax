package com.guandan.game.config;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * WebSocket跨域配置器
 * 负责人：成员B（通讯与架构）
 *
 * <p>负责WebSocket握手阶段的跨域配置。
 * 允许跨域来源访问WebSocket端点，确保前端（不同端口/域名）能正常建立连接。
 */
@Slf4j
@Component
public class WebSocketServerConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(
            ServerEndpointConfig sec,
            HandshakeRequest request,
            HandshakeResponse response
    ) {
        log.debug("WebSocket握手请求来自: {}", request.getHeaders().get("Origin"));

        response.getHeaders().put("Access-Control-Allow-Origin", Collections.singletonList("*"));
        response.getHeaders().put("Access-Control-Allow-Methods", Collections.singletonList("GET, POST, OPTIONS"));
        response.getHeaders().put("Access-Control-Allow-Headers", Collections.singletonList("*"));
        response.getHeaders().put("Access-Control-Allow-Credentials", Collections.singletonList("true"));

        super.modifyHandshake(sec, request, response);
    }
}
