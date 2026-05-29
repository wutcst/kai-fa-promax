package com.guandan.game.websocket;

import com.guandan.game.model.GameRoom;
import com.guandan.game.service.GameLogicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 断线重连管理器
 * 负责人：成员B（通讯与架构）
 *
 * <p>功能：
 * <ul>
 *   <li>处理玩家断线重连请求</li>
 *   <li>恢复玩家房间信息和手牌状态</li>
 *   <li>清除连接清理后的过期数据</li>
 * </ul>
 *
 * <p>重连流程：
 * <ol>
 *   <li>客户端发送 RECONNECT 消息</li>
 *   <li>查询 SessionManager 中的玩家信息</li>
 *   <li>如果玩家在房间中 → 恢复游戏状态</li>
 *   <li>返回房间ID、玩家手牌等恢复数据</li>
 * </ol>
 */
@Slf4j
@Component
public class ReconnectManager {

    @Autowired
    private GameLogicService gameLogicService;

    @Autowired
    private SessionManager sessionManager;

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

                log.info("玩家 {} 重连成功，恢复房间 {}", playerId, room.getRoomId());
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
}
