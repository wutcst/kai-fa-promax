package com.guandan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guandan.common.Result;
import com.guandan.service.PlayerService;
import com.guandan.vo.PageResult;
import com.guandan.vo.PlayerGameRecordVO;
import com.guandan.vo.PlayerStatisticsVO;
import com.guandan.vo.PlayerTrendVO;
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
//
// ===== 阶段标记 =====
// Phase 2 — 个人中心价值提升
// 职责: 提供胜率统计和战绩分页查询 API
// 边界: 统计信息依赖 UserStats 表，不涉及游戏逻辑
// 验收项:
//   ✅ 胜率统计接口 (/api/player/statistics)
//   ✅ 战绩列表接口 (/api/player/records) — 支持分页 + 时间筛选
//   ✅ 空数据兜底 — statistics 返回空对象，records 返回空页
//   ✅ 异常兜底 — Controller 层 try-catch 统一返回友好提示
// =====
// Phase 3 — 玩家趋势统计
// 新增:
//   ✅ 趋势统计接口 (/api/player/trend) — 连胜/连败 + 热力图
//   ✅ 空数据兜底 — 趋势返回空对象
// =====
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
            log.info("战绩分页查询成功: page={}, pageSize={}, total={}, totalPages={}", page, pageSize, result.getTotal(), result.getTotalPages());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取玩家战绩记录异常: {}", e.getMessage(), e);
            return Result.error("获取战绩记录失败，请稍后重试");
        }
    }

    @GetMapping("/player/trend")
    @Operation(summary = "获取玩家趋势统计", description = "获取当前登录玩家的连胜/连败趋势分析和近7天对局热力图数据")
    public Result<PlayerTrendVO> getTrend() {
        try {
            PlayerTrendVO trend = playerService.getPlayerTrend();
            if (trend == null) {
                return Result.success(new PlayerTrendVO());
            }
            return Result.success(trend);
        } catch (Exception e) {
            log.error("获取玩家趋势统计异常: {}", e.getMessage(), e);
            return Result.error("获取趋势统计失败，请稍后重试");
        }
    }
}
