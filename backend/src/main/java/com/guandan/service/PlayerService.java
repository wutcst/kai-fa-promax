package com.guandan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guandan.entity.UserStats;
import com.guandan.mapper.UserStatsMapper;
import com.guandan.util.UserContext;
import com.guandan.vo.PlayerGameRecordVO;
import com.guandan.vo.PlayerStatisticsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
public class PlayerService {

    @Autowired
    private UserStatsMapper userStatsMapper;

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
}
