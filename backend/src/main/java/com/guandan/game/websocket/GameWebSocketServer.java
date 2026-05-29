package com.guandan.game.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/game/{roomNo}")
public class GameWebSocketServer {
    @OnOpen
    public void onOpen(Session session) {}

    @OnMessage
    public void onMessage(String message, Session session) {
        session.getAsyncRemote().sendText(message);
    }

    @OnClose
    public void onClose(Session session) {}
}
