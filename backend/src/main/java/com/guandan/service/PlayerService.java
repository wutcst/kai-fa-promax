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
