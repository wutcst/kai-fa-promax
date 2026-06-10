package com.guandan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guandan.entity.UserStats;
import com.guandan.vo.PlayerGameRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserStatsMapper extends BaseMapper<UserStats> {
    Page<PlayerGameRecordVO> selectRecordsByUserId(@Param("userId") Long userId,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime,
                                                     Page<PlayerGameRecordVO> page);

    /**
     * 查询最近 N 局游戏记录（用于趋势分析）
     * @param userId 玩家ID
     * @param limit 限制条数
     * @return 游戏记录列表
     */
    List<PlayerGameRecordVO> selectRecentRecords(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 查询近 7 天每日对局统计
     * @param userId 玩家ID
     * @param startDate 开始日期
     * @return 每日统计
     */
    List<java.util.Map<String, Object>> selectDailyStats(@Param("userId") Long userId,
                                                          @Param("startDate") LocalDateTime startDate);

    /**
     * 查询近 7 天每小时对局分布
     * @param userId 玩家ID
     * @param startDate 开始日期
     * @return 小时分布数据
     */
    List<java.util.Map<String, Object>> selectHourlyDistribution(@Param("userId") Long userId,
                                                                  @Param("startDate") LocalDateTime startDate);
}
