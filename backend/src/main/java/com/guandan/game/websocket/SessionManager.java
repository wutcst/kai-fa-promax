package com.guandan.game.websocket;

import javax.websocket.Session;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;

public class SessionManager {
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> roomPlayerMap = new ConcurrentHashMap<>();

    public static void addSession(String playerId, Session session) { sessionMap.put(playerId, session); }
    public static void removeSession(String playerId) { sessionMap.remove(playerId); }
    public static Session getSession(String playerId) { return sessionMap.get(playerId); }
}
// SessionManager: thread-safe session registry by playerId
