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
 * 职责：提供快速匹配功能的 RESTful API 入口。
 * 包括加入匹配队列、取消匹配、查询匹配状态和匹配结果。
 *
 * 核心流程：
 * 1. 玩家调用 /api/match/join 加入匹配队列
 * 2. 系统在队列中等待收集满 4 名玩家
 * 3. 收集完成后自动创建房间并将玩家加入
 * 4. 玩家通过 /api/match/result 轮询获取匹配结果
 * 5. 玩家可随时调用 /api/match/cancel 取消匹配
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
     * @return 匹配到的房间号
     */
    @PostMapping("/match/result")
    public Result<Map<String, Object>> getMatchResult() {
        Long userId = UserContext.getUserId();
        String roomNo = matchService.getMatchResult(userId);
        if (roomNo != null) {
            log.info("玩家 {} 匹配成功，房间号: {}", userId, roomNo);
            return Result.success(Collections.singletonMap("roomNo", roomNo));
        } else {
            return Result.success(null);
        }
    }
}
