package com.guandan.controller;

import com.guandan.annotation.LogRecord;
import com.guandan.common.Result;
import com.guandan.service.MatchService;
import com.guandan.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Tag(name = "快速匹配", description = "快速匹配功能，自动匹配玩家")
@RestController
@RequestMapping("/api")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Operation(summary = "加入匹配队列", description = "加入快速匹配队列，系统自动匹配其他玩家")
    @PostMapping("/match/join")
    @LogRecord(operationType = "JOIN_MATCH", operationModule = "MATCH", operationDesc = "加入快速匹配队列")
    public Result<Boolean> joinMatch() {
        Long userId = UserContext.getUserId();
        log.info("收到加入匹配队列请求: userId={}", userId);

        boolean success = matchService.joinMatchQueue(userId);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("加入匹配队列失败，请稍后重试");
        }
    }

    @Operation(summary = "取消匹配", description = "从匹配队列中移除，取消快速匹配")
    @PostMapping("/match/cancel")
    @LogRecord(operationType = "CANCEL_MATCH", operationModule = "MATCH", operationDesc = "取消快速匹配")
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

    @Operation(summary = "查询匹配状态", description = "查询当前用户是否在匹配队列中")
    @PostMapping("/match/status")
    public Result<Boolean> getMatchStatus() {
        Long userId = UserContext.getUserId();
        boolean inQueue = matchService.isInMatchQueue(userId);
        return Result.success(inQueue);
    }

    @Operation(summary = "查询匹配结果", description = "查询当前用户的匹配结果，返回匹配到的房间号")
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
// Controller: POST /match/join and POST /match/cancel
// Fix: handle cancel on already-matched player
// Refactor: rename match endpoints for consistency
// Docs: match API error codes and retry guidance
// Regression: MatchController cancel and polling flow verification
