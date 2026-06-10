package com.guandan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guandan.entity.UserStats;
import com.guandan.mapper.UserStatsMapper;
import com.guandan.util.UserContext;
import com.guandan.vo.PlayerGameRecordVO;
import com.guandan.vo.PlayerStatisticsVO;
import com.guandan.vo.PlayerTrendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
//
// ===== 阶段标记 =====
// Phase 2 — 个人中心价值提升
// 职责: 玩家统计信息查询和战绩分页查询
// 边界:
//   - 仅涉及 UserStats 表查询，不写操作
//   - 分页查询限制最大页码 10000，防止恶意深度分页
//   - 空数据和异常场景统一返回空 Page（不抛错）
// 依赖: UserStatsMapper.selectRecordsByUserId
// 验收项:
//   ✅ 胜率计算逻辑 (winGames / totalGames * 100)
//   ✅ 空 UserStats 兜底 — 返回全 0 统计
//   ✅ 分页边界保护 — page < 1 重置为 1，pageSize > 100 截断
//   ✅ 异常隔离 — Mapper 层异常 catch 后返回空 Page
// =====
// Phase 3 — 玩家趋势统计
// 新增:
//   ✅ 连胜/连败趋势分析 (getPlayerTrend)
//   ✅ 最近 N 局胜负序列遍历判定
//   ✅ 历史最大连胜/连败统计
//   ✅ 近 7 天每日对局统计（对局热力图数据源）
//   ✅ 近 7 天小时分布统计
// =====
public class PlayerService {

    @Autowired
    private UserStatsMapper userStatsMapper;

    /**
     * 最近趋势分析的局数
     */
    private static final int TREND_RECENT_LIMIT = 50;

    /**
     * 热力图统计的天数
     */
    private static final int HEATMAP_DAYS = 7;

    public PlayerStatisticsVO getStatistics() {
        Long userId = UserContext.getUserId();
        UserStats stats = userStatsMapper.selectById(userId);

        PlayerStatisticsVO vo = new PlayerStatisticsVO();
        if (stats != null) {
            vo.setTotalGames(stats.getTotalGames());
            vo.setWinGames(stats.getWinGames());
            vo.setWinRate(stats.getTotalGames() > 0
                ? (double) stats.getWinGames() / stats.getTotalGames() * 100
                : 0.0);
            vo.setLevelCurrent(stats.getLevelCurrent());
        } else {
            vo.setTotalGames(0);
            vo.setWinGames(0);
            vo.setWinRate(0.0);
            vo.setLevelCurrent(2);
        }

        return vo;
    }

    public Page<PlayerGameRecordVO> getRecords(Integer page, Integer pageSize, LocalDateTime startTime, LocalDateTime endTime) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            log.warn("查询玩家战绩记录：用户未登录");
            return new Page<>(0, pageSize, 0);
        }

        // 分页边界保护：限制最大查询深度，防止超大页码导致性能问题
        if (page == null || page < 1) page = 1;
        if (pageSize == null || pageSize < 1) pageSize = 10;
        if (pageSize > 100) pageSize = 100;
        if (page > 10000) page = 10000; // 防止恶意超大页码

        log.info("查询玩家战绩记录: userId={}, page={}, pageSize={}, startTime={}, endTime={}", userId, page, pageSize, startTime, endTime);

        Page<PlayerGameRecordVO> pageParam = new Page<>(page, pageSize);

        Page<PlayerGameRecordVO> result;
        try {
            result = userStatsMapper.selectRecordsByUserId(userId, startTime, endTime, pageParam);
        } catch (Exception e) {
            log.error("查询玩家战绩记录异常: userId={}", userId, e);
            return new Page<>(0, pageSize, 0);
        }

        if (result == null) {
            log.warn("查询玩家战绩记录结果为空: userId={}", userId);
            return new Page<>(0, pageSize, 0);
        }

        log.info("查询结果: total={}, records={}", result.getTotal(), result.getRecords());

        return result;
    }

    // ============================================================
    //  连胜/连败趋势统计和历史对局热力图
    // ============================================================

    /**
     * 获取玩家趋势统计
     * 包含连胜/连败趋势和近 7 天对局热力图
     *
     * @return PlayerTrendVO
     */
    public PlayerTrendVO getPlayerTrend() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            log.warn("查询玩家趋势：用户未登录");
            return new PlayerTrendVO();
        }

        PlayerTrendVO trend = new PlayerTrendVO();

        // 1. 获取最近 N 局记录
        List<PlayerGameRecordVO> recentRecords;
        try {
            recentRecords = userStatsMapper.selectRecentRecords(userId, TREND_RECENT_LIMIT);
        } catch (Exception e) {
            log.error("查询最近记录异常: userId={}", userId, e);
            recentRecords = new ArrayList<>();
        }

        // 2. 分析连胜/连败趋势
        analyzeStreakTrend(trend, recentRecords);

        // 3. 获取近 7 天热力图数据
        LocalDateTime startDate = LocalDateTime.of(LocalDate.now().minusDays(HEATMAP_DAYS), LocalTime.MIN);
        try {
            List<Map<String, Object>> dailyStats = userStatsMapper.selectDailyStats(userId, startDate);
            // 补充缺失的日期（无对局的日期补 0）
            trend.setDailyStats(fillMissingDays(dailyStats, HEATMAP_DAYS));
        } catch (Exception e) {
            log.error("查询每日统计异常: userId={}", userId, e);
            trend.setDailyStats(new ArrayList<>());
        }

        try {
            List<Map<String, Object>> hourlyDistribution = userStatsMapper.selectHourlyDistribution(userId, startDate);
            trend.setHourlyDistribution(fillMissingHours(hourlyDistribution));
        } catch (Exception e) {
            log.error("查询小时分布异常: userId={}", userId, e);
            trend.setHourlyDistribution(new ArrayList<>());
        }

        return trend;
    }

    /**
     * 分析连胜/连败趋势
     * 遍历最近 N 局结果的逆向序列（最新在前），统计当前连续胜负
     */
    private void analyzeStreakTrend(PlayerTrendVO trend, List<PlayerGameRecordVO> records) {
        // 提取胜负序列
        List<Boolean> results = records.stream()
                .map(r -> r.getResult() != null && r.getResult() == 1)
                .collect(Collectors.toList());
        trend.setRecentResults(results);

        if (results.isEmpty()) {
            trend.setStreakCount(0);
            trend.setStreakType("none");
            trend.setMaxWinStreak(0);
            trend.setMaxLoseStreak(0);
            return;
        }

        // 计算当前连胜/连败（从最新一局开始）
        boolean firstResult = results.get(0);
        int currentStreak = 0;
        for (Boolean r : results) {
            if (r == firstResult) {
                currentStreak++;
            } else {
                break;
            }
        }

        if (firstResult) {
            trend.setStreakCount(currentStreak);
            trend.setStreakType(currentStreak > 0 ? "win_streak" : "none");
        } else {
            trend.setStreakCount(-currentStreak);
            trend.setStreakType(currentStreak > 0 ? "lose_streak" : "none");
        }

        // 计算历史最大连胜/连败
        int maxWin = 0, maxLose = 0;
        int tempWin = 0, tempLose = 0;
        for (Boolean r : results) {
            if (r) {
                tempWin++;
                tempLose = 0;
                maxWin = Math.max(maxWin, tempWin);
            } else {
                tempLose++;
                tempWin = 0;
                maxLose = Math.max(maxLose, tempLose);
            }
        }
        trend.setMaxWinStreak(maxWin);
        trend.setMaxLoseStreak(maxLose);

        log.info("趋势分析结果: currentStreak={}, type={}, maxWin={}, maxLose={}",
                trend.getStreakCount(), trend.getStreakType(), maxWin, maxLose);
    }

    /**
     * 补全缺失的日期（热力图数据）
     * 将 Mapper 返回的日期补充为完整的 HEATMAP_DAYS 天序列
     */
    private List<Map<String, Object>> fillMissingDays(List<Map<String, Object>> dailyStats, int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 将已有的数据转为 Map<dateKey, record>
        Map<String, Map<String, Object>> existingMap = new HashMap<>();
        if (dailyStats != null) {
            for (Map<String, Object> row : dailyStats) {
                Object key = row.get("date_key");
                if (key != null) {
                    existingMap.put(key.toString(), row);
                }
            }
        }

        // 遍历最近 days 天
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateKey = date.toString();
            Map<String, Object> entry;
            if (existingMap.containsKey(dateKey)) {
                entry = existingMap.get(dateKey);
            } else {
                entry = new HashMap<>();
                entry.put("date_key", dateKey);
                entry.put("wins", 0);
                entry.put("losses", 0);
                entry.put("total", 0);
            }
            result.add(entry);
        }

        return result;
    }

    /**
     * 补全缺失的小时段（0-23）
     */
    private List<Map<String, Object>> fillMissingHours(List<Map<String, Object>> hourlyDistribution) {
        List<Map<String, Object>> result = new ArrayList<>();

        Map<Integer, Map<String, Object>> existingMap = new HashMap<>();
        if (hourlyDistribution != null) {
            for (Map<String, Object> row : hourlyDistribution) {
                Object key = row.get("hour_key");
                if (key instanceof Number) {
                    existingMap.put(((Number) key).intValue(), row);
                }
            }
        }

        for (int h = 0; h < 24; h++) {
            Map<String, Object> entry;
            if (existingMap.containsKey(h)) {
                entry = existingMap.get(h);
            } else {
                entry = new HashMap<>();
                entry.put("hour_key", h);
                entry.put("wins", 0);
                entry.put("losses", 0);
                entry.put("total", 0);
            }
            result.add(entry);
        }

        return result;
    }
}
