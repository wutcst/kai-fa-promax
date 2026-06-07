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
// WS: onOpen register session, onClose cleanup and notify room
// Fix: handle session close on game room disband
// Refactor: extract message handler from WebSocket server
// Docs: WebSocket message format: {type:'play|pass|deal|ready', payload:{}}
// Test: WebSocket session lifecycle verification
// Chore: WebSocket server configuration consolidation
// Chore: WebSocket server stage delivery materials
