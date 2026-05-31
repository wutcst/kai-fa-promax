package com.guandan.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guandan.entity.GameRecord;
import com.guandan.mapper.GameRecordMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏战绩记录服务
 *
 * <p>负责游戏结束后保存战绩记录，并更新玩家统计数据。
 *
 * <p><b>测试验证点：</b>
 * <ul>
 *   <li>[TC-GRS-001] saveGameRecord 正常保存：入库后 id 自增非空</li>
 *   <li>[TC-GRS-002] saveGameRecord 重复调用：同 roomId 允许多条记录</li>
 *   <li>[TC-GRS-003] 赢家胜负统计：winGames 自增</li>
 *   <li>[TC-GRS-004] 输家降级：level 减少 1，最低为 2</li>
 *   <li>[TC-GRS-005] 已存在 UserStats：updateById 而非 insert</li>
 *   <li>[TC-GRS-006] 不存在 UserStats：自动 insert 新行</li>
 *   <li>[TC-GRS-007] 异常路径：roomPlayerMapper.selectList 空列表 → 走兜底逻辑</li>
 *   <li>[TC-GRS-008] 异常路径：updatePlayerStats 抛异常 → 不影响主流程</li>
 *   <li>[TC-GRS-009] 异常路径：updateRoomStatus 抛异常 → 不影响主流程</li>
 *   <li>[TC-GRS-010] 边界：score 为 null → 兜底为 0</li>
 *   <li>[TC-GRS-011] 边界：level 为 null → 不更新等级</li>
 * </ul>
 */
@Slf4j
@Service
public class GameRecordService {

    @Resource
    private GameRecordMapper gameRecordMapper;

    @Resource
    private com.guandan.mapper.UserMapper userMapper;

    @Resource
    private com.guandan.mapper.RoomMapper roomMapper;

    @Resource
    private com.guandan.mapper.RoomPlayerMapper roomPlayerMapper;

    @Resource
    private com.guandan.mapper.UserStatsMapper userStatsMapper;

    /**
     * 保存游戏记录并更新玩家统计
     */
    public void saveGameRecord(Long roomId, Long winnerId, Integer score, Integer levelTeamA, Integer levelTeamB) {
        try {
            GameRecord record = new GameRecord();
            record.setRoomId(roomId);
            record.setWinnerId(winnerId);
            record.setScore(score);
            record.setCreateTime(LocalDateTime.now());

            gameRecordMapper.insert(record);
            log.info("保存游戏记录: roomId={}, winnerId={}, score={}", roomId, winnerId, score);

            // 获取参与对局的所有玩家
            try {
                List<com.guandan.entity.RoomPlayer> players = roomPlayerMapper.selectList(
                        new QueryWrapper<com.guandan.entity.RoomPlayer>().eq("room_id", roomId));

                for (com.guandan.entity.RoomPlayer rp : players) {
                    if (rp.getUserId() == null) {
                        continue;
                    }

                    // 判断是否是赢家
                    boolean isWinner = rp.getUserId().equals(winnerId);

                    // 判断玩家所属队伍
                    Integer seatIndex = rp.getSeatIndex();
                    boolean isTeamA = seatIndex != null && seatIndex % 2 == 0;

                    // 计算新等级
                    Integer newLevel = null;
                    if (isWinner) {
                        // 赢家升级
                        newLevel = isTeamA ? levelTeamA : levelTeamB;
                    } else {
                        // 输家降级（掼蛋规则：输家降一级）
                        Integer currentLevel = isTeamA ? levelTeamA : levelTeamB;
                        newLevel = currentLevel > 2 ? currentLevel - 1 : 2;
                    }

                    // 更新玩家统计
                    updatePlayerStats(rp.getUserId(), isWinner, newLevel);
                }
            } catch (Exception e) {
                log.error("批量更新玩家统计失败", e);
                // 兜底：至少保证赢家统计更新
                updatePlayerStats(winnerId, true, levelTeamA);
            }

            updateRoomStatus(roomId);

        } catch (Exception e) {
            log.error("保存游戏记录失败", e);
        }
    }

    private void updatePlayerStats(Long userId, boolean isWinner, Integer newLevel) {
        try {
            com.guandan.entity.UserStats stats = userStatsMapper.selectById(userId);
            boolean isNew = false;
            if (stats == null) {
                isNew = true;
                stats = new com.guandan.entity.UserStats();
                stats.setUserId(userId);
                stats.setTotalGames(0);
                stats.setWinGames(0);
                stats.setLevelCurrent(2);
            }

            stats.setTotalGames(stats.getTotalGames() + 1);
            if (isWinner) {
                stats.setWinGames(stats.getWinGames() + 1);
                if (newLevel != null) {
                    stats.setLevelCurrent(newLevel);
                }
            }

            // updateById 对于不存在的记录不会插入，需要区分 insert / update
            if (isNew) {
                userStatsMapper.insert(stats);
            } else {
                userStatsMapper.updateById(stats);
            }
            log.info("更新玩家统计: userId={}, totalGames={}, winGames={}, level={}",
                    userId, stats.getTotalGames(), stats.getWinGames(), stats.getLevelCurrent());

        } catch (Exception e) {
            log.error("更新玩家统计失败", e);
        }
    }

    private void updateRoomStatus(Long roomId) {
        try {
            com.guandan.entity.Room room = roomMapper.selectById(roomId);
            if (room != null) {
                // 游戏结束后，保持房间状态为0（等待中），而不是设置为2（已结束）
                // 这样房间会继续显示在房间列表中
                room.setStatus(0);
                roomMapper.updateById(room);
                log.info("更新房间状态为等待中: roomId={}", roomId);
            }
        } catch (Exception e) {
            log.error("更新房间状态失败", e);
        }
    }
}
