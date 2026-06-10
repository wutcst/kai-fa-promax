package com.guandan.game.websocket;

import com.guandan.game.model.GameRoom;
import com.guandan.game.service.GameLogicService;
import com.guandan.game.util.CardUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断线重连管理器（增强版）
 * 负责人：成员B（通讯与架构）
 *
 * <p>功能：
 * <ul>
 *   <li>处理玩家断线重连请求</li>
 *   <li>恢复玩家房间信息和手牌状态</li>
 *   <li>玩家重连期间座位锁定，防止被抢占</li>
 *   <li>恢复完整的房间状态（手牌、座位、当前轮次、级牌等）</li>
 *   <li>清除连接清理后的过期数据</li>
 * </ul>
 *
 * <p>重连流程：
 * <ol>
 *   <li>客户端发送 RECONNECT 消息</li>
 *   <li>查询 SessionManager 中的玩家信息</li>
 *   <li>锁定玩家座位（其他玩家不能入座）</li>
 *   <li>如果玩家在房间中 → 恢复游戏状态</li>
 *   <li>返回房间ID、手牌、座位、轮次等完整状态数据</li>
 *   <li>重连成功后释放座位锁或转为在线状态</li>
 * </ol>
 *
 * <p>座位锁定机制：
 * <ul>
 *   <li>玩家断线后，其座位立即被锁定</li>
 *   <li>锁定期间其他玩家不能加入该座位</li>
 *   <li>锁定超时（默认180秒）后自动释放</li>
 *   <li>玩家重连成功后座位锁解除</li>
 * </ul>
 */
@Slf4j
@Component
public class ReconnectManager {

    @Autowired
    private GameLogicService gameLogicService;

    @Autowired
    private SessionManager sessionManager;

    /**
     * 座位锁定信息
     */
    private static class SeatLock {
        /** 玩家ID */
        final String playerId;
        /** 房间ID */
        final String roomId;
        /** 座位索引 */
        final int seatIndex;
        /** 锁定时间 */
        final long lockTime;
        /** 锁定是否过期 */
        volatile boolean expired;

        SeatLock(String playerId, String roomId, int seatIndex) {
            this.playerId = playerId;
            this.roomId = roomId;
            this.seatIndex = seatIndex;
            this.lockTime = System.currentTimeMillis();
            this.expired = false;
        }

        boolean isExpired() {
            return expired || (System.currentTimeMillis() - lockTime) > SEAT_LOCK_TIMEOUT_MS;
        }
    }

    /**
     * 座位锁定超时时间（毫秒）
     */
    private static final long SEAT_LOCK_TIMEOUT_MS = 180_000;

    /**
     * 座位锁容器：roomId -> (seatIndex -> SeatLock)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, SeatLock>> seatLocks = new ConcurrentHashMap<>();

    /**
     * 玩家断线时锁定其座位
     *
     * @param playerId  玩家ID
     * @param roomId    房间ID
     * @param seatIndex 座位索引
     * @return true 如果锁定成功
     */
    public boolean lockSeat(String playerId, String roomId, int seatIndex) {
        if (playerId == null || roomId == null) {
            return false;
        }

        // 检查该座位是否已被其他玩家锁定
        ConcurrentHashMap<Integer, SeatLock> roomLocks = seatLocks.get(roomId);
        if (roomLocks != null) {
            SeatLock existingLock = roomLocks.get(seatIndex);
            if (existingLock != null && !existingLock.isExpired() && !existingLock.playerId.equals(playerId)) {
                log.warn("座位锁定失败：房间 {} 座位 {} 已被玩家 {} 锁定", roomId, seatIndex, existingLock.playerId);
                return false;
            }
        }

        seatLocks.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(seatIndex, new SeatLock(playerId, roomId, seatIndex));

        log.info("玩家 {} 座位锁定：房间={}, 座位={}", playerId, roomId, seatIndex);
        return true;
    }

    /**
     * 解除玩家座位锁定
     *
     * @param playerId 玩家ID
     * @param roomId   房间ID
     */
    public void unlockSeat(String playerId, String roomId) {
        if (playerId == null || roomId == null) {
            return;
        }

        ConcurrentHashMap<Integer, SeatLock> roomLocks = seatLocks.get(roomId);
        if (roomLocks == null) {
            return;
        }

        roomLocks.entrySet().removeIf(entry ->
                entry.getValue().playerId.equals(playerId));

        if (roomLocks.isEmpty()) {
            seatLocks.remove(roomId);
        }

        log.info("玩家 {} 座位锁已解除：房间={}", playerId, roomId);
    }

    /**
     * 检查座位是否被锁定（被其他玩家锁定）
     *
     * @param roomId    房间ID
     * @param seatIndex 座位索引
     * @return true 如果该座位已被锁定
     */
    public boolean isSeatLocked(String roomId, int seatIndex) {
        ConcurrentHashMap<Integer, SeatLock> roomLocks = seatLocks.get(roomId);
        if (roomLocks == null) {
            return false;
        }
        SeatLock lock = roomLocks.get(seatIndex);
        return lock != null && !lock.isExpired();
    }

    /**
     * 获取指定座位的锁定玩家ID
     *
     * @param roomId    房间ID
     * @param seatIndex 座位索引
     * @return 锁定该座位的玩家ID，未被锁定返回 null
     */
    public String getSeatLockOwner(String roomId, int seatIndex) {
        ConcurrentHashMap<Integer, SeatLock> roomLocks = seatLocks.get(roomId);
        if (roomLocks == null) {
            return null;
        }
        SeatLock lock = roomLocks.get(seatIndex);
        return (lock != null && !lock.isExpired()) ? lock.playerId : null;
    }

    /**
     * 清理过期座位锁
     */
    public void cleanExpiredLocks() {
        for (Map.Entry<String, ConcurrentHashMap<Integer, SeatLock>> roomEntry : seatLocks.entrySet()) {
            String roomId = roomEntry.getKey();
            ConcurrentHashMap<Integer, SeatLock> roomLocks = roomEntry.getValue();

            roomLocks.entrySet().removeIf(entry -> {
                boolean expired = entry.getValue().isExpired();
                if (expired) {
                    log.info("座位锁已过期释放：房间={}, 座位={}, 玩家={}",
                            roomId, entry.getKey(), entry.getValue().playerId);
                }
                return expired;
            });

            if (roomLocks.isEmpty()) {
                seatLocks.remove(roomId);
            }
        }
    }

    /**
     * 获取房间被锁定座位的数量
     */
    public int getLockedSeatCount(String roomId) {
        ConcurrentHashMap<Integer, SeatLock> roomLocks = seatLocks.get(roomId);
        if (roomLocks == null) {
            return 0;
        }
        return (int) roomLocks.values().stream().filter(l -> !l.isExpired()).count();
    }

    /**
     * 处理重连请求
     *
     * @param playerId 玩家ID
     * @return 包含重连结果的Map（status, roomId, handCards等）
     */
    public Map<String, Object> handleReconnect(String playerId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 查询Session信息
            SessionManager.SessionInfo sessionInfo = sessionManager.getSession(playerId);
            if (sessionInfo == null) {
                log.warn("重连失败：玩家 {} 没有找到会话信息", playerId);
                result.put("status", "failed");
                result.put("message", "未找到会话信息");
                return result;
            }

            // 尝试恢复房间信息
            GameRoom room = gameLogicService.getPlayerRoom(playerId);
            if (room != null) {
                result.put("status", "reconnected");
                result.put("roomId", room.getRoomId());
                result.put("playerId", playerId);

                // 恢复手牌信息
                java.util.List<Integer> handCards = room.getPlayerHandCards(playerId);
                result.put("handCards", handCards != null ? handCards : new java.util.ArrayList<>());

                // 恢复房间状态
                result.put("roomStatus", room.getStatus().name());
                result.put("levelCardRank", room.getLevelCardRank());
                result.put("roomStatus", room.getStatus().name());
                result.put("levelCardRank", room.getLevelCardRank());

                // 恢复当前轮次信息
                result.put("currentPlayerId", room.getCurrentPlayerId());
                result.put("lastHandCards", room.getLastHandCards());
                result.put("lastCardType", room.getLastCardType());
                result.put("lastCardValue", room.getLastCardValue());
                result.put("lastPlayerId", room.getLastPlayerId());
                result.put("consecutivePassCount", room.getConsecutivePassCount());

                // 恢复玩家列表和座位信息
                java.util.List<String> playerIds = room.getPlayerIds();
                result.put("playerIds", playerIds);
                result.put("playerCount", playerIds != null ? playerIds.size() : 0);

                // 恢复队友和对手信息（掼蛋为4人游戏）
                if (playerIds != null) {
                    int myIndex = playerIds.indexOf(playerId);
                    if (myIndex >= 0) {
                        result.put("mySeatIndex", myIndex);
                        // 掼蛋：队友为 index+2%4，对手为 index+1%4 和 index+3%4
                        result.put("teammateId", playerIds.get((myIndex + 2) % 4));
                        result.put("leftOpponentId", playerIds.get((myIndex + 3) % 4));
                        result.put("rightOpponentId", playerIds.get((myIndex + 1) % 4));
                    }
                }

                log.info("玩家 {} 重连成功，恢复房间 {}（状态={}）",
                        playerId, room.getRoomId(), room.getStatus());
            } else {
                // 房间已不存在
                result.put("status", "no_room");
                result.put("playerId", playerId);
                log.info("玩家 {} 重连，但不在任何房间中", playerId);
            }
        } catch (Exception e) {
            log.error("处理重连请求时发生错误", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 恢复玩家完整状态（供重连成功后调用）
     *
     * <p>此方法在重连成功时调用，恢复玩家在房间内的完整游戏状态：
     * <ul>
     *   <li>更新心跳和在线状态</li>
     *   <li>解除座位锁定（玩家已在线）</li>
     *   <li>向其他在线玩家广播重连通知</li>
     * </ul>
     *
     * @param playerId 玩家ID
     * @param roomId   房间ID
     * @return 包含完整恢复状态的Map
     */
    public Map<String, Object> restorePlayerState(String playerId, String roomId) {
        Map<String, Object> stateData = new HashMap<>();

        try {
            // 1. 更新在线状态
            sessionManager.reconnect(playerId);
            sessionManager.updateHeartbeat(playerId);

            // 2. 解除座位锁定
            unlockSeat(playerId, roomId);

            // 3. 获取房间最新状态
            GameRoom room = gameLogicService.getRoom(roomId);
            if (room != null) {
                stateData.put("roomId", roomId);
                stateData.put("status", "restored");

                // 恢复手牌
                java.util.List<Integer> handCards = room.getPlayerHandCards(playerId);
                stateData.put("handCards", handCards != null
                        ? new java.util.ArrayList<>(handCards) : new java.util.ArrayList<>());

                // 恢复游戏状态
                stateData.put("gameStatus", room.getStatus().name());
                stateData.put("currentPlayerId", room.getCurrentPlayerId());
                stateData.put("levelCardRank", room.getLevelCardRank());
                stateData.put("lastHandCards", room.getLastHandCards());
                stateData.put("lastCardType", room.getLastCardType());
                stateData.put("consecutivePassCount", room.getConsecutivePassCount());

                // 恢复所有玩家的手牌数量
                java.util.Map<String, Integer> handCardCounts = new HashMap<>();
                java.util.List<String> allPlayers = room.getPlayerIds();
                if (allPlayers != null) {
                    for (String pid : allPlayers) {
                        java.util.List<Integer> hand = room.getPlayerHandCards(pid);
                        handCardCounts.put(pid, hand != null ? hand.size() : 0);
                    }
                }
                stateData.put("handCardCounts", handCardCounts);

                // 恢复桌面状态
                java.util.List<Integer> lastHand = room.getLastHandCards();
                if (lastHand != null && !lastHand.isEmpty()) {
                    stateData.put("lastPlayedCards", new java.util.ArrayList<>(lastHand));
                    stateData.put("lastPlayerId", room.getLastPlayerId());
                }

                log.info("玩家 {} 状态已完整恢复：房间={}, 手牌数={}, 当前回合={}",
                        playerId, roomId,
                        handCards != null ? handCards.size() : 0,
                        room.getCurrentPlayerId());
            } else {
                stateData.put("status", "room_not_found");
                stateData.put("message", "房间已不存在");
                log.warn("恢复玩家状态失败：房间 {} 不存在", roomId);
            }
        } catch (Exception e) {
            log.error("恢复玩家状态时发生错误", e);
            stateData.put("status", "error");
            stateData.put("message", e.getMessage());
        }

        return stateData;
    }
}
