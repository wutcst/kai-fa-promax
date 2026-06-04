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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
@Tag(name = "玩家信息", description = "玩家统计信息相关接口")
public class PlayerController {

    @jakarta.annotation.Resource
    private PlayerService playerService;

    @GetMapping("/player/statistics")
    @Operation(summary = "获取玩家统计信息", description = "获取当前登录玩家的统计信息")
    public Result<PlayerStatisticsVO> getStatistics() {
        PlayerStatisticsVO statistics = playerService.getStatistics();
        return Result.success(statistics);
    }

    @GetMapping("/player/records")
    @Operation(summary = "获取玩家战绩记录", description = "获取当前登录玩家的战绩记录，支持分页和时间筛选")
    public Result<PageResult<PlayerGameRecordVO>> getRecords(
            @Parameter(description = "页码，从1开始") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页大小，默认20，最多100") @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @Parameter(description = "开始时间，格式：yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式：yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Page<PlayerGameRecordVO> records = playerService.getRecords(page, pageSize, startTime, endTime);
        PageResult<PlayerGameRecordVO> result = PageResult.of(records);
        return Result.success(result);
    }
}
