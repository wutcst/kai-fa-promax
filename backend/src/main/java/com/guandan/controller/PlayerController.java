package com.guandan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guandan.common.Result;
import com.guandan.service.PlayerService;
import com.guandan.vo.PageResult;
import com.guandan.vo.PlayerGameRecordVO;
import com.guandan.vo.PlayerStatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "玩家信息", description = "玩家统计信息相关接口")
public class PlayerController {

    @jakarta.annotation.Resource
    private PlayerService playerService;

    @GetMapping("/player/statistics")
    @Operation(summary = "获取玩家统计信息", description = "获取当前登录玩家的统计信息")
    public Result<PlayerStatisticsVO> getStatistics() {
        try {
            PlayerStatisticsVO statistics = playerService.getStatistics();
            if (statistics == null) {
                // 兜底：返回空统计对象
                return Result.success(new PlayerStatisticsVO());
            }
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取玩家统计信息异常: {}", e.getMessage(), e);
            return Result.error("获取统计信息失败，请稍后重试");
        }
    }

    @GetMapping("/player/records")
    @Operation(summary = "获取玩家战绩记录", description = "获取当前登录玩家的战绩记录，支持分页和时间筛选")
    public Result<PageResult<PlayerGameRecordVO>> getRecords(
            @Parameter(description = "页码，从1开始") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页大小，默认20，最多100") @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @Parameter(description = "开始时间，格式：yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式：yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        try {
            // 参数校验：page和pageSize下限兜底
            if (page == null || page < 1) page = 1;
            if (pageSize == null || pageSize < 1) pageSize = 20;
            if (pageSize > 100) pageSize = 100;

            Page<PlayerGameRecordVO> records = playerService.getRecords(page, pageSize, startTime, endTime);
            PageResult<PlayerGameRecordVO> result = PageResult.of(records);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取玩家战绩记录异常: {}", e.getMessage(), e);
            return Result.error("获取战绩记录失败，请稍后重试");
        }
    }
}
