package com.guandan.game.websocket;

import jakarta.websocket.Session;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket会话管理器
 * 负责人：成员B（通讯与架构）
 *
 * <p>功能：
 * <ul>
 *   <li>管理所有WebSocket连接（在线/离线状态）</li>
 *   <li>心跳检测（定期探测，发现超时自动标记离线）</li>
 *   <li>断线重连支持（保留会话数据，支持RECONNECT流程）</li>
 *   <li>会话信息统计（在线人数、会话总数、重连次数）</li>
 * </ul>
 *
 * <p><b>会话生命周期：</b>
 * <ol>
 *   <li>连接建立 → addSession (playerId → SessionInfo)</li>
 *   <li>正常活动 → updateHeartbeat 定时更新心跳时间</li>
 *   <li>心跳超时（60秒无活动）→ markOffline，断开连接</li>
 *   <li>重连请求 → reconnect，恢复为在线状态</li>
 *   <li>正常关闭 → removeSession，完全清理</li>
 *   <li>离线超过5分钟 → 自动清理会话数据</li>
 * </ol>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>重复连接同一playerId → 覆盖旧Session引用</li>
 *   <li>向已关闭Session发消息 → 自动清理引用</li>
 *   <li>空playerId → 不作处理</li>
 *   <li>房间不存在 → 对应房间玩家映射为空</li>
 *   <li>心跳检测中超时 → 自动清理，无需外部干预</li>
 * </ul>
 */
@Slf4j
@Component
public class SessionManager {

    /**
     * 会话信息存储：playerId -> SessionInfo
     */
    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    /**
     * WebSocket Session引用存储：playerId -> Session
     * 用于实际发送消息
     */
    private final ConcurrentHashMap<String, Session> webSocketSessions = new ConcurrentHashMap<>();

    /**
     * 房间到玩家的映射：roomId -> Set<playerId>
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> roomPlayers = new ConcurrentHashMap<>();

    /**
     * 心跳检测间隔（秒）
     */
    private static final int HEARTBEAT_INTERVAL = 30;

    /**
     * 连接超时时间（秒）- 超过这个时间没心跳认为断线
     */
    private static final int CONNECTION_TIMEOUT = 60;

    /**
     * 断线保留时间（秒）- 断线后多久清理数据
     */
    private static final int DISCONNECTED_RETENTION_TIME = 300; // 5分钟

    /**
     * 定时线程池
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 在线玩家数量
     */
    private final AtomicInteger onlineCount = new AtomicInteger(0);

    /**
     * 初始化：启动心跳检测任务
     */
    public SessionManager() {
        scheduler.scheduleAtFixedRate(this::heartbeatCheck, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        log.info("SessionManager初始化完成，心跳检测已启动（间隔{}秒）", HEARTBEAT_INTERVAL);
    }

    /**
     * 添加会话
     */
    public void addSession(String playerId, String roomId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            log.warn("addSession: playerId 为空，跳过");
            return;
        }

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setPlayerId(playerId);
        sessionInfo.setRoomId(roomId);
        sessionInfo.setConnectTime(System.currentTimeMillis());
        sessionInfo.setLastHeartbeatTime(System.currentTimeMillis());
        sessionInfo.setOnline(true);

        sessions.put(playerId, sessionInfo);

        if (roomId != null && !roomId.isEmpty()) {
            roomPlayers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                    .put(playerId, true);
        }

        int count = onlineCount.incrementAndGet();
        log.info("玩家 {} 加入房间 {}, 当前在线人数: {}", playerId, roomId, count);
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat(String playerId) {
        SessionInfo sessionInfo = sessions.get(playerId);
        if (sessionInfo != null) {
            sessionInfo.setLastHeartbeatTime(System.currentTimeMillis());
            log.debug("玩家 {} 心跳更新", playerId);
        }
    }

    /**
     * 标记会话为离线（不删除数据，支持重连）
     */
    public void markOffline(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            return;
        }

        SessionInfo sessionInfo = sessions.get(playerId);
        if (sessionInfo != null && sessionInfo.isOnline()) {
            sessionInfo.setOnline(false);
            sessionInfo.setDisconnectTime(System.currentTimeMillis());

            int count = onlineCount.decrementAndGet();
            log.info("玩家 {} 断开连接，当前在线人数: {}", playerId, count);
        }
    }

    /**
     * 重连：将会话标记为在线
     */
    public boolean reconnect(String playerId) {
        SessionInfo sessionInfo = sessions.get(playerId);
        if (sessionInfo != null && !sessionInfo.isOnline()) {
            sessionInfo.setOnline(true);
            sessionInfo.setLastHeartbeatTime(System.currentTimeMillis());
            sessionInfo.setReconnectCount(sessionInfo.getReconnectCount() + 1);

            int count = onlineCount.incrementAndGet();
            log.info("玩家 {} 重连成功，重连次数: {}, 当前在线人数: {}",
                    playerId, sessionInfo.getReconnectCount(), count);
            return true;
        }
        return false;
    }

    /**
     * 完全移除会话
     */
    public void removeSession(String playerId) {
        SessionInfo sessionInfo = sessions.remove(playerId);
        if (sessionInfo != null) {
            ConcurrentHashMap<String, Boolean> players = roomPlayers.get(sessionInfo.getRoomId());
            if (players != null) {
                players.remove(playerId);
                if (players.isEmpty()) {
                    roomPlayers.remove(sessionInfo.getRoomId());
                }
            }

            if (sessionInfo.isOnline()) {
                onlineCount.decrementAndGet();
            }
            log.info("玩家 {} 会话已完全移除", playerId);
        }

        Session wsSession = webSocketSessions.remove(playerId);
        if (wsSession != null) {
            log.debug("玩家 {} 的WebSocket Session引用已移除", playerId);
        }
    }

    /**
     * 保存WebSocket Session引用
     */
    public void saveSession(String playerId, Session session) {
        webSocketSessions.put(playerId, session);
        log.debug("玩家 {} 的WebSocket Session引用已保存", playerId);
    }

    /**
     * 移除WebSocket Session引用
     */
    public void removeWebSocketSession(String playerId) {
        Session wsSession = webSocketSessions.remove(playerId);
        if (wsSession != null) {
            log.debug("玩家 {} 的WebSocket Session引用已移除", playerId);
        }
    }

    /**
     * 更新玩家的房间信息
     */
    public void updatePlayerRoom(String playerId, String roomId) {
        SessionInfo sessionInfo = sessions.get(playerId);
        if (sessionInfo != null) {
            if (sessionInfo.getRoomId() != null && !sessionInfo.getRoomId().isEmpty()) {
                ConcurrentHashMap<String, Boolean> oldRoomPlayers = roomPlayers.get(sessionInfo.getRoomId());
                if (oldRoomPlayers != null) {
                    oldRoomPlayers.remove(playerId);
                    if (oldRoomPlayers.isEmpty()) {
                        roomPlayers.remove(sessionInfo.getRoomId());
                    }
                }
            }

            sessionInfo.setRoomId(roomId);

            if (roomId != null && !roomId.isEmpty()) {
                roomPlayers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                        .put(playerId, true);
            }

            log.info("玩家 {} 房间信息已更新: {}", playerId, roomId);
        }
    }

    /**
     * 获取WebSocket Session引用
     */
    public Session getWebSocketSession(String playerId) {
        Session session = webSocketSessions.get(playerId);
        if (session != null && !session.isOpen()) {
            webSocketSessions.remove(playerId);
            log.warn("玩家 {} 的WebSocket Session已关闭，移除引用", playerId);
            return null;
        }
        return session;
    }

    /**
     * 获取会话信息
     */
    public SessionInfo getSession(String playerId) {
        return sessions.get(playerId);
    }

    /**
     * 检查玩家是否在线
     */
    public boolean isOnline(String playerId) {
        SessionInfo sessionInfo = sessions.get(playerId);
        return sessionInfo != null && sessionInfo.isOnline();
    }

    /**
     * 获取房间内的所有玩家（包括离线的）
     */
    public ConcurrentHashMap<String, Boolean> getRoomPlayers(String roomId) {
        return roomPlayers.get(roomId);
    }

    /**
     * 获取当前在线人数
     */
    public int getOnlineCount() {
        return onlineCount.get();
    }

    /**
     * 获取会话统计信息
     */
    public SessionStats getStats() {
        SessionStats stats = new SessionStats();
        stats.setOnlineCount(onlineCount.get());
        stats.setTotalSessions(sessions.size());
        stats.setRoomCount(roomPlayers.size());

        int offlineCount = 0;
        int reconnectCount = 0;
        for (SessionInfo info : sessions.values()) {
            if (!info.isOnline()) {
                offlineCount++;
            }
            reconnectCount += info.getReconnectCount();
        }

        stats.setOfflineCount(offlineCount);
        stats.setTotalReconnects(reconnectCount);
        return stats;
    }

    /**
     * 心跳检测：定期检查并清理超时连接
     */
    private void heartbeatCheck() {
        long currentTime = System.currentTimeMillis();
        int timeoutCount = 0;
        int cleanedCount = 0;

        for (Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            SessionInfo sessionInfo = entry.getValue();

            if (sessionInfo.isOnline()) {
                long elapsed = (currentTime - sessionInfo.getLastHeartbeatTime()) / 1000;
                if (elapsed > CONNECTION_TIMEOUT) {
                    log.warn("玩家 {} 心跳超时（{}秒未活动），标记为离线",
                            entry.getKey(), elapsed);
                    markOffline(entry.getKey());
                    timeoutCount++;
                }
            } else {
                long disconnectedTime = (currentTime - sessionInfo.getDisconnectTime()) / 1000;
                if (disconnectedTime > DISCONNECTED_RETENTION_TIME) {
                    log.info("玩家 {} 离线超过{}秒，清理会话", entry.getKey(), disconnectedTime);
                    removeSession(entry.getKey());
                    cleanedCount++;
                }
            }
        }

        if (timeoutCount > 0 || cleanedCount > 0) {
            log.info("心跳检测完成：超时断线={}, 清理会话={}, 当前在线={}",
                    timeoutCount, cleanedCount, getOnlineCount());
        }
    }

    /**
     * 获取所有会话信息（用于调试）
     */
    public ConcurrentHashMap<String, SessionInfo> getAllSessions() {
        return sessions;
    }

    /**
     * 获取会话统计信息
     */
    public SessionStats getStats() {
        SessionStats stats = new SessionStats();
        stats.setOnlineCount(onlineCount.get());
        stats.setTotalSessions(sessions.size());
        stats.setRoomCount(roomPlayers.size());

        int offlineCount = 0;
        int reconnectCount = 0;
        for (SessionInfo info : sessions.values()) {
            if (!info.isOnline()) {
                offlineCount++;
            }
            reconnectCount += info.getReconnectCount();
        }

        stats.setOfflineCount(offlineCount);
        stats.setTotalReconnects(reconnectCount);
        return stats;
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        log.info("SessionManager已关闭");
    }

    // ============================================================
    //  连接管理方法
    // ============================================================

    /**
     * 建立连接：保存会话并设置在线
     */
    public void establishConnection(String playerId, Session session, String roomId) {
        saveSession(playerId, session);
        addSession(playerId, roomId);
    }

    /**
     * 断开连接：标记离线并保留数据（支持重连）
     */
    public boolean disconnectSession(String playerId) {
        markOffline(playerId);
        removeWebSocketSession(playerId);
        return true;
    }

    /**
     * 完全清理连接：移除所有引用和数据
     */
    public boolean cleanSession(String playerId) {
        removeSession(playerId);
        removeWebSocketSession(playerId);
        return true;
    }

    // ============================================================
    //  广播管理方法
    // ============================================================

    /**
     * 向指定玩家发送消息（在 Session 有效时）
     */
    public boolean sendMessage(String playerId, String message) {
        Session session = getWebSocketSession(playerId);
        if (session == null || !session.isOpen()) {
            log.warn("sendMessage: 玩家 {} 的 Session 不可用", playerId);
            return false;
        }
        try {
            session.getBasicRemote().sendText(message);
            return true;
        } catch (IOException e) {
            log.warn("sendMessage: 向玩家 {} 发送消息失败", playerId, e);
            return false;
        }
    }

    /**
     * 向房间内所有在线玩家广播消息
     */
    public boolean broadcastToOnlinePlayers(String roomId, String message) {
        ConcurrentHashMap<String, Boolean> players = roomPlayers.get(roomId);
        if (players == null || players.isEmpty()) {
            log.warn("broadcastToOnlinePlayers: 房间 {} 无玩家", roomId);
            return false;
        }
        int successCount = 0;
        for (String playerId : players.keySet()) {
            if (isOnline(playerId) && sendMessage(playerId, message)) {
                successCount++;
            }
        }
        log.debug("broadcastToOnlinePlayers: 房间 {}, 成功发送给 {} 人", roomId, successCount);
        return successCount > 0;
    }

    /**
     * 会话信息
     */
    @Data
    public static class SessionInfo {
        private String playerId;
        private String roomId;
        private long connectTime;
        private long lastHeartbeatTime;
        private long disconnectTime;
        private boolean online;
        private int reconnectCount;
    }

    /**
     * 会话统计信息
     */
    @Data
    public static class SessionStats {
        private int onlineCount;
        private int offlineCount;
        private int totalSessions;
        private int roomCount;
        private int totalReconnects;
    }
}
