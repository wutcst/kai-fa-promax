package com.guandan.service;

import com.guandan.dto.NewGameRequest;
import com.guandan.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 快速匹配服务
 *
 * 职责：管理匹配队列，当收集到4名玩家时自动创建房间并完成匹配。
 * 提供加入/取消队列、查询匹配状态和结果的接口。
 */
@Slf4j
@Service
public class MatchService {

    @Autowired
    private RoomCache roomCache;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomService roomService;

    /**
     * 加入匹配队列
     *
     * @param userId 用户ID
     * @return 是否成功加入
     */
    public boolean joinMatchQueue(Long userId) {
        if (userId == null) {
            return false;
        }
        roomCache.addToMatchQueue(userId);
        log.info("玩家 {} 加入匹配队列，当前队列人数: {}", userId, roomCache.getMatchQueueSize());

        // 检查是否达到4人，满足则触发匹配
        checkAndMatch();

        return true;
    }

    /**
     * 取消匹配
     *
     * @param userId 用户ID
     * @return 是否成功取消
     */
    public boolean cancelMatch(Long userId) {
        roomCache.removeFromMatchQueue(userId);
        log.info("玩家 {} 取消匹配，当前队列人数: {}", userId, roomCache.getMatchQueueSize());
        return true;
    }

    /**
     * 查询用户是否在匹配队列中
     *
     * @param userId 用户ID
     * @return 是否在队列中
     */
    public boolean isInMatchQueue(Long userId) {
        return roomCache.isInMatchQueue(userId);
    }

    /**
     * 获取匹配结果
     *
     * @param userId 用户ID
     * @return 匹配到的房间号，null表示尚未匹配成功
     */
    public String getMatchResult(Long userId) {
        return roomCache.getMatchResult(userId);
    }

    /**
     * 检查匹配队列，如果达到4人则创建房间并匹配
     * 使用 synchronized 确保线程安全
     */
    public synchronized void checkAndMatch() {
        Set<Long> queue = roomCache.getMatchQueue();
        log.info("检查匹配队列，当前队列: {}, 队列大小: {}", queue, queue != null ? queue.size() : 0);

        if (queue == null || queue.size() < 4) {
            log.info("队列人数不足4人，当前: {}人，等待更多玩家...", queue != null ? queue.size() : 0);
            return;
        }

        // 取前4个玩家进行匹配
        List<Long> matchedPlayers = new ArrayList<>();
        int count = 0;
        for (Long playerId : queue) {
            if (count >= 4) break;
            matchedPlayers.add(playerId);
            count++;
        }

        log.info("从队列中提取玩家进行匹配: {}", matchedPlayers);

        if (matchedPlayers.size() == 4) {
            log.info("========== 开始匹配流程，4个玩家: {} ==========", matchedPlayers);

            try {
                // 创建房间（使用第一个玩家的ID作为创建者）
                NewGameRequest request = new NewGameRequest();
                request.setUserId(matchedPlayers.get(0));
                request.setIsPrivate(false);
                request.setConfig(null);

                String roomNo = roomService.createRoom(request);
                log.info("匹配房间创建成功! roomNo={}", roomNo);

                // 将4个玩家都加入数据库房间
                for (Long userId : matchedPlayers) {
                    try {
                        roomService.joinRoom(roomNo, userId);
                        log.info("玩家 {} 成功加入房间 {}", userId, roomNo);
                    } catch (Exception e) {
                        log.error("玩家 {} 加入房间失败: {}", userId, e.getMessage());
                    }
                }

                // 从队列中移除并设置匹配结果
                for (Long playerId : matchedPlayers) {
                    roomCache.removeFromMatchQueue(playerId);
                    roomCache.setMatchResult(playerId, roomNo);
                    log.info("玩家 {} 匹配成功，房间号: {}", playerId, roomNo);
                }

                log.info("========== 匹配成功! 房间号: {}, 4个玩家: {} ==========", roomNo, matchedPlayers);
            } catch (Exception e) {
                log.error("========== 匹配失败! ==========", e);
                // 匹配失败时重新将玩家加入队列
                for (Long playerId : matchedPlayers) {
                    if (!roomCache.isInMatchQueue(playerId)) {
                        roomCache.addToMatchQueue(playerId);
                    }
                }
            }
        }
    }
}
