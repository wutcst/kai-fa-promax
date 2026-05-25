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
 * 提供加入/取消队列、查询匹配状态和获取匹配结果的接口。
 *
 * ── 方法职责边界 ────────────────────────────────────────
 * - joinMatchQueue()     : 加入匹配队列（入口），含重复提交和已匹配检测
 * - cancelMatch()        : 取消匹配，清理队列和结果缓存
 * - isInMatchQueue()     : 查询是否在队列中
 * - getMatchResult()     : 获取匹配结果（房间号），供前端轮询
 * - checkAndMatch()      : 核心匹配逻辑，被 ScheduleConfig.pollMatchQueue() 定时或
 *                           joinMatchQueue() 加入满员时触发，synchronized 保证线程安全
 * ─────────────────────────────────────────────────────────
 *
 * ── 匹配队列配置说明 ──────────────────────────────────────
 * - 队列存储: roomCache 内存存储（非持久化），系统重启后清空
 * - 匹配条件: 队列收集满 4 人时触发 checkAndMatch
 * - 线程安全: checkAndMatch 使用 synchronized 关键字
 * - 幂等入队: isInMatchQueue 检测已在队列中时直接返回 true
 * - 超时机制: 前端 60 秒轮询超时，服务端不主动清理队列
 * ─────────────────────────────────────────────────────────
 *
 * ── 取消匹配配置说明 ──────────────────────────────────────
 * - 取消时同步清理队列记录和结果缓存（removeFromMatchQueue + removeMatchResult）
 * - 不在队列中取消时 log.warn 记录警告日志，返回 false
 * - 取消后用户可重新加入匹配队列
 * ─────────────────────────────────────────────────────────
 *
 * ── 结果轮询配置说明 ──────────────────────────────────────
 * - 匹配成功后设置匹配结果到 roomCache.setMatchResult
 * - 前端通过 POST /api/match/result 轮询获取
 * - 返回 null 表示尚未匹配成功，前端继续轮询
 * - 前端消费结果后由 controller 层清理缓存（当前由前端负责）
 * ─────────────────────────────────────────────────────────
 *
 * 核心流程：
 * 1. joinMatchQueue → 加入匹配队列 → 触发 checkAndMatch 检查是否满4人
 * 2. cancelMatch → 从队列移除并清理匹配结果
 * 3. checkAndMatch → 取前4人创建房间 → 全部加入 → 设置每个玩家的匹配结果
 * 4. getMatchResult → 返回匹配到的房间号（轮询用）
 *
 * 线程安全：checkAndMatch 使用 synchronized 确保并发安全
 *
 * ## 回归验证点
 * - [TC-MATCH-001] joinMatchQueue 加入队列 → roomCache.addToMatchQueue 执行
 * - [TC-MATCH-002] 用户已在房间中时加入匹配 → Controller 层由 UserContext 校验
 * - [TC-MATCH-003] 重复加入 → isInMatchQueue 检测返回 true（幂等）
 * - [TC-MATCH-006] checkAndMatch 满4人 → 创建房间并设置匹配结果
 * - [TC-MATCH-007] 匹配成功后 getMatchResult → 返回 roomNo
 * - [TC-MATCH-009] synchronized 保证线程安全 → 并发下不会重复创建多组房间
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
     * <p>职责：将用户加入匹配队列的入口方法。
     * 上游由 {@link com.guandan.controller.MatchController#joinMatch()} 调用。</p>
     *
     * <h4>状态一致性判断</h4>
     * <ul>
     *   <li><b>重复提交检测</b>：用户已在队列中时直接返回 true（幂等）</li>
     *   <li><b>已匹配用户检测</b>：用户有未消费的匹配结果时优先返回 false</li>
     * </ul>
     *
     * <h4>异常场景</h4>
     * <ul>
     *   <li>userId 为 null → 返回 false</li>
     *   <li>用户不存在（数据库查不到）→ 返回 false 并记录警告日志</li>
     *   <li>用户已有未消费匹配结果 → 返回 false，防止重复入队</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return 是否成功加入
     */
    public boolean joinMatchQueue(Long userId) {
        if (userId == null) {
            return false;
        }

        // 校验用户是否存在
        if (userMapper.selectById(userId) == null) {
            log.warn("用户 {} 不存在，无法加入匹配队列", userId);
            return false;
        }

        // 状态一致性判断：重复提交检测
        if (roomCache.isInMatchQueue(userId)) {
            log.info("用户 {} 已在匹配队列中，幂等忽略", userId);
            return true;
        }

        // 状态一致性判断：已有匹配结果但未消费
        String existingResult = roomCache.getMatchResult(userId);
        if (existingResult != null) {
            log.warn("用户 {} 已有未消费的匹配结果，不能重复加入匹配队列", userId);
            return false;
        }

        roomCache.addToMatchQueue(userId);
        log.info("玩家 {} 加入匹配队列，当前队列人数: {}", userId, roomCache.getMatchQueueSize());

        // 检查是否达到4人，满足则触发匹配（核心判断逻辑）
        checkAndMatch();

        return true;
    }

    /**
     * 取消匹配
     *
     * <p>职责：从匹配队列中移除用户并清理其匹配结果缓存。
     * 上游由 {@link com.guandan.controller.MatchController#cancelMatch()} 调用。</p>
     *
     * <h4>清理策略</h4>
     * <p>同时清理队列记录和结果缓存，避免脏数据残留导致状态不一致。</p>
     *
     * <h4>异常场景</h4>
     * <ul>
     *   <li>userId 为 null → 返回 false</li>
     *   <li>用户不在匹配队列中 → 返回 false 并记录警告日志</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return 是否成功取消
     */
    public boolean cancelMatch(Long userId) {
        if (userId == null) {
            return false;
        }

        // 空值保护：校验用户是否在队列中
        if (!roomCache.isInMatchQueue(userId)) {
            log.warn("玩家 {} 不在匹配队列中，无需取消", userId);
            return false;
        }

        roomCache.removeFromMatchQueue(userId);
        // 同时清理匹配结果缓存，避免状态不一致
        roomCache.removeMatchResult(userId);
        log.info("玩家 {} 取消匹配，当前队列人数: {}", userId, roomCache.getMatchQueueSize());
        return true;
    }

    /**
     * 查询用户是否在匹配队列中
     *
     * <p>职责：供 {@link com.guandan.controller.MatchController#getMatchStatus()} 调用，返回布尔值。
     * 上游也可在 {@link #joinMatchQueue} 中幂等检测时调用。</p>
     *
     * <h4>异常场景</h4>
     * <ul>
     *   <li>userId 为 null → 返回 false（非异常，降级处理）</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return true 表示在队列中
     */
    public boolean isInMatchQueue(Long userId) {
        if (userId == null) {
            return false;
        }
        return roomCache.isInMatchQueue(userId);
    }

    /**
     * 获取匹配结果
     *
     * <p>职责：供前端轮询调用，返回匹配成功的房间号。
     * 上游由 {@link com.guandan.controller.MatchController#getMatchResult()} 调用。</p>
     *
     * <h4>流程说明</h4>
     * <p>匹配成功后由 {@link #checkAndMatch} 设置结果到 {@link com.guandan.config.RoomCache}。
     * 返回 null 表示尚未匹配成功，前端继续轮询。</p>
     *
     * <h4>异常场景</h4>
     * <ul>
     *   <li>userId 为 null → 返回 null（降级处理）</li>
     *   <li>尚未匹配成功 → 返回 null（非异常）</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return 匹配成功的房间号，未匹配成功返回 null
     */
    public String getMatchResult(Long userId) {
        if (userId == null) {
            return null;
        }
        return roomCache.getMatchResult(userId);
    }

    /**
     * 检查并执行匹配（核心匹配逻辑）
     *
     * 职责：检查匹配队列是否达到 4 人，满足条件则创建房间并完成匹配。
     * 触发方式：
     * 1. ScheduleConfig.pollMatchQueue() 每 3 秒定时轮询触发
     * 2. joinMatchQueue() 在加入后立即触发（满员加速）
     *
     * 核心判断逻辑：
     * 1. 获取当前队列快照
     * 2. 判断是否满4人
     * 3. 取前4人作为匹配组
     * 4. 创建房间并依次加入
     * 5. 设置匹配结果
     *
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
                if (roomNo == null || roomNo.isEmpty()) {
                    log.error("匹配失败: 创建房间返回null，无法继续匹配流程");
                    throw new RuntimeException("创建房间失败");
                }
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
