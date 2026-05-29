package com.guandan.game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guandan.game.model.GameRoom;
import com.guandan.game.service.GameLogicService;
import com.guandan.game.util.CardUtils;
import com.guandan.service.GameReferee;
import com.guandan.model.CardType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * WebSocket服务器端点（增强版）
 * 负责人：成员A（核心引擎与逻辑） + 成员B（通讯与架构）
 *
 * <p>功能：
 * <ul>
 *   <li>处理客户端连接、消息接收和广播</li>
 *   <li>心跳检测（定时探测客户端在线状态）</li>
 *   <li>断线重连（异常断开后保留会话数据）</li>
 *   <li>会话管理（在线/离线/重连状态追踪）</li>
 *   <li>跨域支持（WebSocketServerConfigurator）</li>
 * </ul>
 *
 * <p><b>消息协议：</b>
 * <ul>
 *   <li>PLAY_CARD — 出牌请求</li>
 *   <li>JOIN_ROOM — 加入房间</li>
 *   <li>HEARTBEAT — 心跳（服务端回复 pong）</li>
 *   <li>RECONNECT — 重连请求</li>
 *   <li>START_GAME — 开始游戏</li>
 *   <li>GAME_OVER — 游戏结束</li>
 *   <li>SUGGEST_CARDS — 出牌提示</li>
 *   <li>CHAT_MESSAGE — 聊天消息</li>
 * </ul>
 *
 * <p><b>广播事件：</b>
 * <ul>
 *   <li>GAME_START — 游戏开始（含手牌和位置信息）</li>
 *   <li>GAME_END — 游戏结束（含获胜者和升级信息）</li>
 *   <li>PLAYER_ACTION — 玩家出牌广播</li>
 *   <li>PLAYER_DISCONNECT — 玩家断线通知</li>
 *   <li>ROOM_UPDATE — 房间信息更新</li>
 *   <li>TABLE_CLEAR — 清空桌面</li>
 *   <li>TURN_CHANGE — 回合切换</li>
 *   <li>CHAT_MESSAGE — 聊天消息广播</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>服务未注入 → 拒绝连接并关闭Session</li>
 *   <li>空消息/null消息 → 忽略并记录警告</li>
 *   <li>未知消息类型 → 返回错误消息</li>
 *   <li>消息解析失败 → 返回错误消息</li>
 *   <li>ClosedChannelException → 正常关闭不记录错误</li>
 *   <li>重连时房间不存在 → 清除房间信息</li>
 * </ul>
 */
@Slf4j
@Component
@ServerEndpoint(
    value = "/ws/game/{playerId}",
    configurator = WebSocketServerConfigurator.class
)
public class GameWebSocketServer {

    /**
     * JSON解析器
     */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 游戏服务（通过静态方式注入，因为WebSocket是单例）
     */
    private static GameLogicService gameLogicService;

    /**
     * 会话管理器
     */
    private static SessionManager sessionManager;

    /**
     * 重连管理器
     */
    private static ReconnectManager reconnectManager;

    /**
     * 游戏裁判服务（规则验证）
     */
    private static GameReferee gameReferee;

    @Autowired
    public void setGameLogicService(GameLogicService gameLogicService) {
        GameWebSocketServer.gameLogicService = gameLogicService;
    }

    @Autowired
    public void setSessionManager(SessionManager sessionManager) {
        GameWebSocketServer.sessionManager = sessionManager;
    }

    @Autowired
    public void setReconnectManager(ReconnectManager reconnectManager) {
        GameWebSocketServer.reconnectManager = reconnectManager;
    }

    @Autowired
    public void setGameReferee(GameReferee gameReferee) {
        GameWebSocketServer.gameReferee = gameReferee;
    }

    // ============================================================
    //  连接生命周期
    // ============================================================

    /**
     * 连接建立时调用
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("playerId") String playerId) {
        log.info("WebSocket连接建立: playerId={}, sessionId={}", playerId, session.getId());

        // 检查依赖是否已注入
        if (gameLogicService == null || sessionManager == null) {
            log.error("服务未注入，无法处理连接");
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "服务未就绪"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }

        // 保存WebSocket Session引用到SessionManager
        sessionManager.saveSession(playerId, session);

        // 检查是否是重连
        SessionManager.SessionInfo existingSession = sessionManager.getSession(playerId);
        String roomId;

        if (existingSession != null && !existingSession.isOnline()) {
            // 重连场景
            log.info("玩家 {} 检测到重连，恢复会话", playerId);
            roomId = existingSession.getRoomId();

            // 如果玩家之前在房间中，恢复房间信息
            if (roomId != null && !roomId.isEmpty()) {
                // 检查房间是否还存在
                GameRoom room = gameLogicService.getRoom(roomId);
                if (room != null) {
                    // 恢复玩家到房间
                    gameLogicService.joinRoom(playerId, roomId);
                    sessionManager.reconnect(playerId);

                    // 发送重连成功消息（包含完整房间状态）
                    Map<String, Object> reconnectData = new java.util.HashMap<>();
                    reconnectData.put("status", "reconnected");
                    reconnectData.put("roomId", roomId);

                    // 补充重连状态：手牌、当前玩家、游戏状态
                    try {
                        if (room.getStatus() == GameRoom.GameStatus.PLAYING) {
                            List<Integer> myCards = room.getHandCards().get(playerId);
                            reconnectData.put("myCards", myCards != null ? myCards : new java.util.ArrayList<>());
                            reconnectData.put("currentPlayerId", room.getCurrentPlayerId());
                            reconnectData.put("gameStatus", "PLAYING");
                            reconnectData.put("lastHandCards", room.getLastHandCards());
                            reconnectData.put("consecutivePassCount", room.getConsecutivePassCount());
                        }
                    } catch (Exception e) {
                        log.warn("补充重连状态信息失败", e);
                    }

                    sendToPlayer(playerId, new WebSocketMessage("RECONNECT_SUCCESS", reconnectData));

                    // 广播其他玩家该玩家已重连
                    try {
                        Map<String, Object> reconnectNotice = new java.util.HashMap<>();
                        reconnectNotice.put("playerId", playerId);
                        reconnectNotice.put("message", "玩家已重连");
                        for (String pid : room.getPlayerIds()) {
                            if (!pid.equals(playerId)) {
                                sendToPlayer(pid, new WebSocketMessage("PLAYER_RECONNECTED", reconnectNotice));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("广播重连通知失败", e);
                    }
                } else {
                    log.warn("玩家 {} 重连失败，房间 {} 不存在", playerId, roomId);
                    // 房间不存在，清除房间信息
                    sessionManager.addSession(playerId, null);
                }
            } else {
                log.info("玩家 {} 重连，但之前不在任何房间中", playerId);
                sessionManager.reconnect(playerId);
            }
        } else {
            // 新连接场景 - 不自动加入房间，等待客户端主动加入
            log.info("玩家 {} 建立新连接，等待加入房间", playerId);
            sessionManager.addSession(playerId, null);
        }
    }

    /**
     * 接收客户端消息
     */
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("playerId") String playerId) {
        log.debug("收到消息 from {}: {}", playerId, message);

        try {
            if (message == null || message.trim().isEmpty()) {
                log.warn("收到空消息 from {}", playerId);
                return;
            }

            // 解析JSON消息
            WebSocketMessage wsMessage = objectMapper.readValue(message, WebSocketMessage.class);

            if (wsMessage == null) {
                log.warn("消息解析失败，结果为 null from {}", playerId);
                sendToPlayer(playerId, WebSocketMessage.error("消息格式错误"));
                return;
            }

            String type = wsMessage.getType();

            if (type == null || type.trim().isEmpty()) {
                log.warn("消息类型为空 from {}: {}", playerId, message);
                sendToPlayer(playerId, WebSocketMessage.error("消息类型不能为空"));
                return;
            }

            switch (type) {
                case "PLAY_CARD":
                    handlePlayCard(playerId, wsMessage.getData());
                    break;
                case "JOIN_ROOM":
                    handleJoinRoom(playerId, wsMessage.getData());
                    break;
                case "HEARTBEAT":
                    handleHeartbeat(playerId);
                    break;
                case "RECONNECT":
                    handleReconnect(playerId);
                    break;
                case "START_GAME":
                    handleStartGame(playerId, wsMessage.getData());
                    break;
                case "GAME_OVER":
                    handleGameOver(playerId, wsMessage.getData());
                    break;
                case "SUGGEST_CARDS":
                    handleSuggestCards(playerId, wsMessage.getData());
                    break;
                case "CHAT_MESSAGE":
                    handleChatMessage(playerId, wsMessage.getData());
                    break;
                default:
                    log.warn("未知的消息类型: {}", type);
                    sendToPlayer(playerId, WebSocketMessage.error("未知的消息类型: " + type));
            }
        } catch (Exception e) {
            log.error("处理消息时发生错误, message: {}", message, e);
            sendToPlayer(playerId, WebSocketMessage.error("消息解析错误: " + e.getMessage()));
        }
    }

    /**
     * 连接关闭时调用
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason, @PathParam("playerId") String playerId) {
        log.info("WebSocket连接关闭: playerId={}, sessionId={}, closeReason={}",
                playerId, session.getId(), closeReason);

        boolean isNormalClose = false;
        try {
            if (closeReason != null && closeReason.getCloseCode() != null) {
                int code = closeReason.getCloseCode().getCode();
                // 1000 Normal closure / 1001 Going away（关闭页面/刷新常见）
                isNormalClose = (code == 1000 || code == 1001);
            }
        } catch (Exception ignored) {
        }

        GameRoom room = null;
        try {
            room = gameLogicService.getPlayerRoom(playerId);
        } catch (Exception ignored) {
        }

        if (isNormalClose) {
            // 正常关闭：视为主动离开房间（删除房间玩家记录，避免房间一直显示"游戏中"）
            try {
                gameLogicService.removePlayer(playerId);
            } catch (Exception ignored) {
            }
            try {
                sessionManager.removeSession(playerId);
            } catch (Exception ignored) {
            }
        } else {
            // 异常断线：标记离线，保留数据支持重连
            sessionManager.markOffline(playerId);
        }

        // 通知房间内其他玩家
        if (room != null) {
            Map<String, Object> disconnectData = new java.util.HashMap<>();
            disconnectData.put("playerId", playerId);
            disconnectData.put("reason", isNormalClose ? "玩家离开" : "玩家掉线");
            WebSocketMessage message = new WebSocketMessage("PLAYER_DISCONNECT", disconnectData);
            broadcastToRoom(room, playerId, message);

            // 广播房间信息更新
            try {
                broadcastRoomInfoUpdate(room.getRoomId());
            } catch (Exception ignored) {
            }

            // 房间没人了 / 或者游戏中有人离开：把数据库房间状态置为结束，避免大厅一直显示"游戏中"
            try {
                String roomNo = room.getRoomId().replace("room_", "");
                com.guandan.entity.Room dbRoom = com.guandan.spring.SpringContextHolder.getBean(com.guandan.service.RoomService.class)
                        .getRoomByRoomNo(roomNo);
                if (dbRoom != null) {
                    int cnt = com.guandan.spring.SpringContextHolder.getBean(com.guandan.service.RoomService.class)
                            .getPlayerCount(dbRoom.getId());
                    if (cnt <= 0 || dbRoom.getStatus() == 1) {
                        com.guandan.spring.SpringContextHolder.getBean(com.guandan.service.RoomService.class)
                                .updateRoomStatus(dbRoom.getId(), 2);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("playerId") String playerId) {
        if (error instanceof java.nio.channels.ClosedChannelException) {
            log.info("WebSocket连接已关闭: playerId={}", playerId);
        } else {
            log.error("WebSocket错误: playerId={}", playerId, error);
        }

        try {
            sessionManager.removeWebSocketSession(playerId);
        } catch (Exception ignored) {
        }
        try {
            sessionManager.markOffline(playerId);
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    //  消息处理
    // ============================================================

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(String playerId) {
        sessionManager.updateHeartbeat(playerId);
        log.debug("玩家 {} 心跳更新", playerId);

        Session session = sessionManager.getWebSocketSession(playerId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(new WebSocketMessage("pong", null));
                session.getBasicRemote().sendText(json);
                log.debug("发送心跳响应给玩家 {}", playerId);
            } catch (Exception e) {
                log.debug("发送心跳响应失败: playerId={}", playerId);
            }
        }
    }

    /**
     * 处理重连请求
     */
    private void handleReconnect(String playerId) {
        if (reconnectManager == null) {
            sendToPlayer(playerId, WebSocketMessage.error("重连服务未就绪"));
            return;
        }
        Map<String, Object> reconnectData = reconnectManager.handleReconnect(playerId);
        sendToPlayer(playerId, new WebSocketMessage("RECONNECT_SUCCESS", reconnectData));
    }

    /**
     * 处理出牌消息
     */
    private void handlePlayCard(String playerId, Object data) {
        if (gameLogicService == null) {
            sendToPlayer(playerId, WebSocketMessage.error("服务器未就绪"));
            return;
        }

        try {
            List<Integer> cardIds = null;
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                if (dataMap.containsKey("cards")) {
                    @SuppressWarnings("unchecked")
                    List<Integer> cards = (List<Integer>) dataMap.get("cards");
                    cardIds = cards;
                }
            }

            boolean success = gameLogicService.playCards(playerId, cardIds);

            if (success) {
                GameRoom room = gameLogicService.getPlayerRoom(playerId);
                if (room != null) {
                    Map<String, Object> actionData = new java.util.HashMap<>();
                    actionData.put("playerId", playerId);
                    actionData.put("cards", cardIds != null ? cardIds : new java.util.ArrayList<>());
                    WebSocketMessage playerActionMsg = new WebSocketMessage("PLAYER_ACTION", actionData);

                    for (String player : room.getPlayerIds()) {
                        sendToPlayer(player, playerActionMsg);
                    }

                    String nextPlayerId = gameLogicService.nextTurn(room);
                    log.info("下一回合玩家: {}", nextPlayerId);

                    if (nextPlayerId != null) {
                        for (String player : room.getPlayerIds()) {
                            Map<String, Object> turnData = new java.util.HashMap<>();
                            turnData.put("currentPlayerId", nextPlayerId);
                            turnData.put("myTurn", player.equals(nextPlayerId));
                            WebSocketMessage turnMsg = new WebSocketMessage("TURN_CHANGE", turnData);
                            sendToPlayer(player, turnMsg);
                        }
                    }
                }
            } else {
                // 获取详细的错误原因提示
                GameRoom room = gameLogicService.getPlayerRoom(playerId);
                if (room != null) {
                    String currentPlayerId = room.getCurrentPlayerId();
                    if (!playerId.equals(currentPlayerId)) {
                        sendToPlayer(playerId, WebSocketMessage.error("现在不是你的回合！"));
                        return;
                    }

                    // 判断具体错误类型
                    List<Integer> hand = room.getPlayerHandCards(playerId);
                    List<Integer> lastHand = room.getLastHandCards();
                    int levelCardRank = room.getLevelCardRank();

                    if (cardIds == null) {
                        sendToPlayer(playerId, WebSocketMessage.error("请选择要出的牌"));
                    } else if (hand == null || !hand.containsAll(cardIds)) {
                        sendToPlayer(playerId, WebSocketMessage.error("手牌中不包含指定的卡牌"));
                    } else if (gameReferee != null && !gameReferee.isValidHand(cardIds, levelCardRank)) {
                        sendToPlayer(playerId, WebSocketMessage.error("牌型不合法，请检查出牌规则"));
                    } else if (lastHand != null && !lastHand.isEmpty() && gameReferee != null
                            && !gameReferee.canBeat(lastHand, cardIds, levelCardRank)) {
                        sendToPlayer(playerId, WebSocketMessage.error("牌太小，无法管住上一手牌"));
                    } else {
                        sendToPlayer(playerId, WebSocketMessage.error("出牌失败，请重试"));
                    }
                } else {
                    sendToPlayer(playerId, WebSocketMessage.error("出牌失败：房间不存在"));
                }
            }
        } catch (Exception e) {
            log.error("处理出牌消息时发生错误", e);
            sendToPlayer(playerId, WebSocketMessage.error("出牌处理错误: " + e.getMessage()));
        }
    }

    /**
     * 处理加入房间消息
     */
    private void handleJoinRoom(String playerId, Object data) {
        if (gameLogicService == null) {
            sendToPlayer(playerId, WebSocketMessage.error("服务器未就绪"));
            return;
        }

        String roomId = "default_room";
        try {
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                if (dataMap.containsKey("roomId")) {
                    roomId = (String) dataMap.get("roomId");
                    if (roomId != null && !roomId.isEmpty() && !roomId.startsWith("room_")) {
                        roomId = "room_" + roomId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析房间ID失败，使用默认房间", e);
        }

        GameRoom existingRoom = gameLogicService.getPlayerRoom(playerId);
        if (existingRoom != null) {
            if (existingRoom.getRoomId().equals(roomId)) {
                log.info("玩家 {} 已经在房间 {} 中", playerId, roomId);
                Map<String, Object> successData = new java.util.HashMap<>();
                successData.put("roomId", roomId);
                successData.put("message", "已在房间中");
                sendToPlayer(playerId, new WebSocketMessage("JOIN_ROOM_SUCCESS", successData));
                return;
            }
            if (existingRoom.getStatus() == GameRoom.GameStatus.PLAYING) {
                log.warn("玩家 {} 正在游戏中，无法加入其他房间", playerId);
                sendToPlayer(playerId, WebSocketMessage.error("您正在游戏中，无法加入其他房间"));
                return;
            }
            log.info("玩家 {} 从房间 {} 移除，准备加入新房间", playerId, existingRoom.getRoomId());
            gameLogicService.removePlayer(playerId);
        }

        GameRoom room = gameLogicService.joinRoom(playerId, roomId);
        sessionManager.updatePlayerRoom(playerId, roomId);

        Map<String, Object> joinSuccessData = new java.util.HashMap<>();
        joinSuccessData.put("roomId", roomId);
        joinSuccessData.put("message", "加入房间成功");
        sendToPlayer(playerId, new WebSocketMessage("JOIN_ROOM_SUCCESS", joinSuccessData));

        broadcastRoomInfoUpdate(roomId);
    }

    /**
     * 处理游戏开始请求
     */
    private void handleStartGame(String playerId, Object data) {
        if (gameLogicService == null) {
            sendToPlayer(playerId, WebSocketMessage.error("服务器未就绪"));
            return;
        }

        String roomId = "default_room";
        try {
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                if (dataMap.containsKey("roomId")) {
                    roomId = (String) dataMap.get("roomId");
                    if (roomId != null && !roomId.isEmpty() && !roomId.startsWith("room_")) {
                        roomId = "room_" + roomId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析房间ID失败，使用默认房间", e);
        }

        boolean started = gameLogicService.startGame(roomId);
        if (started) {
            GameRoom room = gameLogicService.getRoom(roomId);
            if (room != null) {
                broadcastGameStart(room);
                broadcastInitialTurn(roomId);
            }
            log.info("房间 {} 游戏开始", roomId);
        } else {
            log.warn("房间 {} 游戏开始失败", roomId);
            sendToPlayer(playerId, WebSocketMessage.error("游戏开始失败，请稍后重试"));
        }
    }

    /**
     * 处理游戏结束请求
     */
    private void handleGameOver(String playerId, Object data) {
        if (gameLogicService == null) {
            sendToPlayer(playerId, WebSocketMessage.error("服务器未就绪"));
            return;
        }

        GameRoom room = gameLogicService.getPlayerRoom(playerId);
        if (room != null) {
            room.setStatus(GameRoom.GameStatus.FINISHED);
            log.info("房间 {} 游戏结束", room.getRoomId());
        } else {
            log.warn("无法找到玩家 {} 所在的房间", playerId);
        }
    }

    /**
     * 处理出牌提示请求
     */
    private void handleSuggestCards(String playerId, Object data) {
        if (gameLogicService == null) {
            sendToPlayer(playerId, WebSocketMessage.error("服务器未就绪"));
            return;
        }

        GameRoom room = gameLogicService.getPlayerRoom(playerId);
        if (room == null) {
            sendToPlayer(playerId, WebSocketMessage.error("您不在任何房间中"));
            return;
        }

        if (room.getStatus() != GameRoom.GameStatus.PLAYING) {
            sendToPlayer(playerId, WebSocketMessage.error("游戏未开始"));
            return;
        }

        if (!room.isCurrentPlayer(playerId)) {
            sendToPlayer(playerId, WebSocketMessage.error("不是您的回合"));
            return;
        }

        List<Integer> handCards = room.getPlayerHandCards(playerId);
        if (handCards == null || handCards.isEmpty()) {
            sendToPlayer(playerId, new WebSocketMessage("SUGGEST_CARDS_SUCCESS",
                    java.util.Map.of("message", "手牌为空")));
            return;
        }

        // 简单策略：返回第一张最小的牌作为建议
        List<Integer> sorted = new java.util.ArrayList<>(handCards);
        sorted.sort(java.util.Comparator.comparingInt(CardUtils::getRank));
        List<Integer> suggested = sorted.isEmpty() ? null : java.util.Collections.singletonList(sorted.get(0));

        if (suggested != null) {
            sendToPlayer(playerId, new WebSocketMessage("SUGGEST_CARDS_SUCCESS",
                    java.util.Map.of("cards", suggested,
                            "cardType", CardUtils.getCardType(suggested, room.getLevelCardRank()),
                            "message", "建议出牌")));
        } else {
            sendToPlayer(playerId, new WebSocketMessage("SUGGEST_CARDS_SUCCESS",
                    java.util.Map.of("message", "没有合适的牌可出")));
        }
    }

    /**
     * 处理聊天消息
     */
    private void handleChatMessage(String playerId, Object data) {
        try {
            if (data == null) {
                sendToPlayer(playerId, WebSocketMessage.error("聊天消息不能为空"));
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> chatData = (Map<String, Object>) data;
            String message = (String) chatData.get("message");

            if (message == null || message.trim().isEmpty()) {
                sendToPlayer(playerId, WebSocketMessage.error("聊天消息不能为空"));
                return;
            }

            log.info("收到聊天消息: playerId={}, message={}", playerId, message);

            GameRoom room = gameLogicService.getPlayerRoom(playerId);
            if (room == null) {
                sendToPlayer(playerId, WebSocketMessage.error("您不在任何房间中"));
                return;
            }

            Map<String, Object> chatMessageData = new java.util.HashMap<>();
            chatMessageData.put("playerId", playerId);
            chatMessageData.put("message", message);
            chatMessageData.put("type", chatData.getOrDefault("type", "quick"));
            chatMessageData.put("timestamp", System.currentTimeMillis());

            broadcastToRoom(room.getRoomId(), new WebSocketMessage("CHAT_MESSAGE", chatMessageData));
            log.info("聊天消息已广播: roomId={}, playerId={}", room.getRoomId(), playerId);
        } catch (Exception e) {
            log.error("处理聊天消息时发生错误", e);
            sendToPlayer(playerId, WebSocketMessage.error("聊天消息处理错误: " + e.getMessage()));
        }
    }

    // ============================================================
    //  广播方法
    // ============================================================

    /**
     * 广播清空桌面
     */
    public static void broadcastTableClear(String gameRoomId) {
        if (gameLogicService == null) {
            return;
        }

        try {
            GameRoom gameRoom = gameLogicService.getRoom(gameRoomId);
            if (gameRoom == null) {
                return;
            }

            WebSocketMessage msg = new WebSocketMessage("TABLE_CLEAR", new java.util.HashMap<>());
            for (String playerId : gameRoom.getPlayerIds()) {
                sendToPlayer(playerId, msg);
            }
        } catch (Exception e) {
            log.warn("广播清桌面失败: roomId={}", gameRoomId, e);
        }
    }

    /**
     * 广播游戏开始
     */
    private void broadcastGameStart(GameRoom room) {
        if (room == null || room.getPlayerIds() == null || room.getPlayerIds().isEmpty()) {
            log.warn("游戏开始广播失败：房间为空或没有玩家");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < room.getPlayerIds().size(); i++) {
            String playerId = room.getPlayerIds().get(i);
            try {
                List<Integer> myCards = room.getHandCards().get(playerId);
                if (myCards == null) {
                    log.error("玩家 {} 的手牌数据不存在", playerId);
                    failCount++;
                    continue;
                }

                Map<String, String> playerPositions = new java.util.HashMap<>();
                playerPositions.put("我", playerId);
                playerPositions.put("右对手", room.getPlayerIds().get((i + 1) % 4));
                playerPositions.put("队友", room.getPlayerIds().get((i + 2) % 4));
                playerPositions.put("左对手", room.getPlayerIds().get((i + 3) % 4));

                Map<String, Object> startData = new java.util.HashMap<>();
                startData.put("myCards", myCards);
                startData.put("playerId", playerId);
                startData.put("roomId", room.getRoomId());
                startData.put("levelCard", room.getLevelCardRank());
                startData.put("playerPositions", playerPositions);

                WebSocketMessage message = new WebSocketMessage("GAME_START", startData);
                sendToPlayer(playerId, message);
                successCount++;
                log.info("向玩家 {} 发送游戏开始消息，手牌数: {}", playerId, myCards.size());
            } catch (Exception e) {
                log.error("向玩家 {} 发送游戏开始消息失败", playerId, e);
                failCount++;
            }
        }

        log.info("房间 {} 游戏开始广播完成：成功={}, 失败={}", room.getRoomId(), successCount, failCount);
    }

    /**
     * 广播初始回合信息
     */
    private void broadcastInitialTurn(String gameRoomId) {
        if (gameLogicService == null) {
            log.warn("服务未初始化，无法广播回合信息");
            return;
        }
        GameRoom room = gameLogicService.getRoom(gameRoomId);
        if (room == null) {
            return;
        }

        String currentPlayerId = room.getCurrentPlayerId();
        if (currentPlayerId == null) {
            return;
        }

        for (String playerId : room.getPlayerIds()) {
            Map<String, Object> turnData = new java.util.HashMap<>();
            turnData.put("currentPlayerId", currentPlayerId);
            turnData.put("myTurn", playerId.equals(currentPlayerId));
            sendToPlayer(playerId, new WebSocketMessage("TURN_CHANGE", turnData));
        }
    }

    /**
     * 广播房间信息更新（静态方法，供其他类调用）
     */
    public static void broadcastRoomInfoUpdateStatic(String gameRoomId) {
        if (gameLogicService == null) {
            log.warn("服务未初始化，无法广播房间更新");
            return;
        }

        try {
            GameRoom gameRoom = gameLogicService.getRoom(gameRoomId);
            if (gameRoom == null) {
                return;
            }

            int playerCount = gameRoom.getPlayerIds().size();

            Map<String, Object> updateData = new java.util.HashMap<>();
            updateData.put("playerCount", playerCount);
            updateData.put("playerIds", new java.util.ArrayList<>(gameRoom.getPlayerIds()));
            updateData.put("roomId", gameRoomId);

            WebSocketMessage updateMessage = new WebSocketMessage("ROOM_UPDATE", updateData);
            for (String playerId : gameRoom.getPlayerIds()) {
                sendToPlayer(playerId, updateMessage);
            }

            log.info("房间 {} 信息已广播：人数={}", gameRoomId, playerCount);
        } catch (Exception e) {
            log.error("广播房间信息更新失败: roomId={}", gameRoomId, e);
        }
    }

    /**
     * 广播游戏结束
     */
    public static void broadcastGameEnd(String gameRoomId, Long winnerId, Integer score,
                                         Integer levelTeamA, Integer levelTeamB) {
        if (gameLogicService == null) {
            log.warn("服务未初始化，无法广播游戏结束");
            return;
        }

        try {
            Map<String, Object> endData = new java.util.HashMap<>();
            endData.put("winnerId", winnerId);
            endData.put("score", score);
            endData.put("roomId", gameRoomId);
            endData.put("levelTeamA", levelTeamA);
            endData.put("levelTeamB", levelTeamB);

            WebSocketMessage endMessage = new WebSocketMessage("GAME_END", endData);
            for (String playerId : gameLogicService.getPlayerIdsInRoom(gameRoomId)) {
                sendToPlayer(playerId, endMessage);
            }

            log.info("房间 {} 游戏结束已广播：获胜者={}, 分数={}", gameRoomId, winnerId, score);
        } catch (Exception e) {
            log.error("广播游戏结束失败: roomId={}", gameRoomId, e);
        }
    }

    /**
     * 处理AI玩家出牌（静态方法，供GameLogicService调用）
     */
    public static void handleAIPlayCard(String aiPlayerId, List<Integer> cardIds) {
        if (gameLogicService == null || sessionManager == null) {
            log.error("服务未初始化，无法处理AI出牌");
            return;
        }

        try {
            GameRoom room = gameLogicService.getPlayerRoom(aiPlayerId);
            if (room == null) {
                log.error("AI玩家 {} 不在任何房间中", aiPlayerId);
                return;
            }

            if (cardIds == null) {
                cardIds = new java.util.ArrayList<>();
            }

            Map<String, Object> actionData = new java.util.HashMap<>();
            actionData.put("playerId", aiPlayerId);
            actionData.put("cards", cardIds);
            WebSocketMessage playerActionMsg = new WebSocketMessage("PLAYER_ACTION", actionData);

            for (String player : room.getPlayerIds()) {
                if (!sessionManager.isOnline(player)) {
                    log.warn("玩家 {} 不在线，跳过发送消息", player);
                    continue;
                }
                sendToPlayer(player, playerActionMsg);
            }

            log.info("AI玩家 {} 出牌已广播给所有玩家", aiPlayerId);
        } catch (Exception e) {
            log.error("处理AI出牌广播时发生错误", e);
        }
    }

    /**
     * 广播房间信息更新
     */
    private void broadcastRoomInfoUpdate(String gameRoomId) {
        try {
            GameRoom gameRoom = gameLogicService.getRoom(gameRoomId);
            if (gameRoom == null) {
                return;
            }

            int playerCount = gameRoom.getPlayerIds().size();

            Map<String, Object> updateData = new java.util.HashMap<>();
            updateData.put("playerCount", playerCount);
            updateData.put("playerIds", new java.util.ArrayList<>(gameRoom.getPlayerIds()));
            updateData.put("roomId", gameRoomId);

            WebSocketMessage updateMessage = new WebSocketMessage("ROOM_UPDATE", updateData);
            broadcastToRoom(gameRoomId, updateMessage);

            log.info("房间 {} 信息已广播：人数={}", gameRoomId, playerCount);
        } catch (Exception e) {
            log.error("广播房间信息更新失败: roomId={}", gameRoomId, e);
        }
    }

    // ============================================================
    //  发送消息
    // ============================================================

    /**
     * 向指定玩家发送消息
     */
    private static void sendToPlayer(String playerId, WebSocketMessage message) {
        try {
            if (sessionManager == null) {
                log.error("SessionManager未初始化，无法发送消息");
                return;
            }

            if (objectMapper == null) {
                log.error("ObjectMapper未初始化，无法发送消息");
                return;
            }

            Session session = sessionManager.getWebSocketSession(playerId);

            if (session == null) {
                log.warn("无法发送消息给玩家 {}：Session不存在或已关闭", playerId);
                return;
            }

            if (!session.isOpen()) {
                log.warn("无法发送消息给玩家 {}：Session已关闭", playerId);
                return;
            }

            String json = objectMapper.writeValueAsString(message);
            session.getBasicRemote().sendText(json);
            log.debug("发送消息给玩家 {}: {}", playerId, json);

        } catch (java.nio.channels.ClosedChannelException | IllegalStateException e) {
            log.info("发送消息给玩家 {} 失败，连接已关闭", playerId);
            try {
                if (sessionManager != null) {
                    sessionManager.removeWebSocketSession(playerId);
                    sessionManager.markOffline(playerId);
                }
            } catch (Exception ignored) {
            }
        } catch (IOException e) {
            log.info("发送消息给玩家 {} 失败(IO)，清理连接引用", playerId);
            try {
                if (sessionManager != null) {
                    sessionManager.removeWebSocketSession(playerId);
                    sessionManager.markOffline(playerId);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.error("发送消息给玩家 {} 时发生错误", playerId, e);
        }
    }

    /**
     * 向房间内所有玩家广播消息
     */
    private void broadcastToRoom(String roomId, WebSocketMessage message) {
        if (roomId == null) {
            log.warn("广播消息失败：房间ID为空");
            return;
        }

        GameRoom room = gameLogicService.getRoom(roomId);
        if (room == null) {
            log.warn("广播消息失败：房间不存在 roomId={}", roomId);
            return;
        }

        for (String playerId : room.getPlayerIds()) {
            sendToPlayer(playerId, message);
        }
    }

    /**
     * 向房间内除指定玩家外的所有玩家广播消息
     */
    private void broadcastToRoom(GameRoom room, String excludePlayerId, WebSocketMessage message) {
        if (room == null || room.getPlayerIds() == null) {
            return;
        }

        for (String playerId : room.getPlayerIds()) {
            if (!playerId.equals(excludePlayerId)) {
                sendToPlayer(playerId, message);
            }
        }
    }

    // ============================================================
    //  内部消息类型（简化版消息封装）
    // ============================================================

    /**
     * WebSocket消息封装
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class WebSocketMessage {
        private String type;
        private Object data;

        public static WebSocketMessage error(String message) {
            return new WebSocketMessage("ERROR", java.util.Map.of("message", message));
        }
    }
}
