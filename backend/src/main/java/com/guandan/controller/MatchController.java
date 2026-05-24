package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.service.MatchService;
import com.guandan.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 快速匹配控制器
 *
 * <p>职责：提供快速匹配功能的 RESTful API 入口。
 * 包括加入匹配队列、取消匹配、查询匹配状态和匹配结果轮询。</p>
 *
 * <h3>职责边界</h3>
 * <ol>
 *   <li><b>参数校验和用户认证</b>：通过 {@link com.guandan.util.UserContext} 获取当前用户</li>
 *   <li><b>结果包装</b>：统一使用 {@code Result<T>} 包装响应</li>
 *   <li><b>不做业务决策</b>：所有匹配逻辑委托给 {@link MatchService}</li>
 * </ol>
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li>玩家调用 {@code POST /api/match/join} 加入匹配队列（由 {@link MatchService#joinMatchQueue} 处理）</li>
 *   <li>系统在队列中等待收集满 4 名玩家（由 {@link com.guandan.config.ScheduleConfig#pollMatchQueue} 定时触发）</li>
 *   <li>收集完成后自动创建房间并将玩家加入（由 {@link MatchService#checkAndMatch} 执行）</li>
 *   <li>玩家通过 {@code POST /api/match/result} 轮询获取匹配结果（由 {@link MatchService#getMatchResult} 返回）</li>
 *   <li>玩家可随时调用 {@code POST /api/match/cancel} 取消匹配（由 {@link MatchService#cancelMatch} 处理）</li>
 * </ol>
 *
 * <h3>匹配队列管理职责链</h3>
 * <ul>
 *   <li>{@code MatchController}：API 入口，负责请求/响应</li>
 *   <li>{@link MatchService}：匹配业务逻辑，负责队列维护和匹配执行</li>
 *   <li>{@link com.guandan.config.ScheduleConfig}：定时触发匹配检测</li>
 *   <li>{@link com.guandan.config.RoomCache}：队列数据存储，线程安全</li>
 * </ul>
 *
 * <h3>异常场景汇总</h3>
 * <ul>
 *   <li>加入匹配时用户未登录 → 返回"用户未登录"</li>
 *   <li>加入匹配时已在队列中 → 幂等返回成功</li>
 *   <li>加入匹配队列失败 → 返回"加入匹配队列失败，请稍后重试"</li>
 *   <li>取消匹配时用户不在队列中 → 返回"取消匹配失败，可能不在匹配队列中"</li>
 *   <li>查询匹配结果时用户未登录 → 返回"用户未登录"</li>
 * </ul>
 *
 * ## 回归验证点
 * - [TC-MATCH-001] 加入匹配队列 → joinMatch 返回 true
 * - [TC-MATCH-003] 重复加入 → isInMatchQueue 检测幂等返回
 * - [TC-MATCH-004] 取消匹配 → cancelMatch 返回 true
 * - [TC-MATCH-005] 不在队列取消匹配 → cancelMatch 返回 false
 * - [TC-MATCH-007] 轮询匹配结果 → getMatchResult 返回房间号
 * - [TC-MATCH-008] 匹配超时 → getMatchResult 返回 null（前端处理超时逻辑）
 *
 * @author 何涛
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class MatchController {

    @Autowired
    private MatchService matchService;

    /**
     * 加入匹配队列
     *
     * POST /api/match/join
     *
     * 流程说明：
     * - 校验用户登录状态
     * - 重复提交检测：已在队列中时幂等返回成功
     * - 调用 MatchService 加入队列
     * - 若队列达到 4 人则自动触发匹配
     *
     * @return 是否成功加入
     */
    @PostMapping("/match/join")
    public Result<Boolean> joinMatch() {
        Long userId = UserContext.getUserId();
        log.info("收到加入匹配队列请求: userId={}", userId);

        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 状态一致性判断：已在队列中则幂等返回
        if (matchService.isInMatchQueue(userId)) {
            log.info("用户 {} 已在匹配队列中，幂等返回", userId);
            return Result.success(true);
        }

        boolean success = matchService.joinMatchQueue(userId);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("加入匹配队列失败，请稍后重试");
        }
    }

    /**
     * 取消匹配
     *
     * POST /api/match/cancel
     *
     * 流程说明：
     * - 校验用户登录状态
     * - 从匹配队列中移除用户
     * - 清理匹配结果缓存
     *
     * @return 是否成功取消
     */
    @PostMapping("/match/cancel")
    public Result<Boolean> cancelMatch() {
        Long userId = UserContext.getUserId();
        log.info("收到取消匹配请求: userId={}", userId);

        boolean success = matchService.cancelMatch(userId);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("取消匹配失败，可能不在匹配队列中");
        }
    }

    /**
     * 查询匹配状态
     *
     * POST /api/match/status
     *
     * 返回用户是否在匹配队列中，前端据此控制轮询逻辑。
     *
     * @return 用户是否在匹配队列中
     */
    @PostMapping("/match/status")
    public Result<Boolean> getMatchStatus() {
        Long userId = UserContext.getUserId();
        boolean inQueue = matchService.isInMatchQueue(userId);
        return Result.success(inQueue);
    }

    /**
     * 查询匹配结果
     *
     * POST /api/match/result
     *
     * 轮询此接口获取匹配结果，匹配成功时返回房间号。
     * 建议轮询间隔：2-3 秒。
     *
     * 空值保护：
     * - userId 为 null 时返回用户未登录错误
     * - matchService.getMatchResult 返回 null 时正常返回空结果
     *
     * @return 匹配到的房间号
     */
    @PostMapping("/match/result")
    public Result<Map<String, Object>> getMatchResult() {
        Long userId = UserContext.getUserId();

        // 空值保护：未登录用户无法查询匹配结果
        if (userId == null) {
            return Result.error("用户未登录");
        }

        String roomNo = matchService.getMatchResult(userId);
        if (roomNo != null) {
            log.info("玩家 {} 匹配成功，房间号: {}", userId, roomNo);
            return Result.success(Collections.singletonMap("roomNo", roomNo));
        } else {
            return Result.success(null);
        }
    }
}
