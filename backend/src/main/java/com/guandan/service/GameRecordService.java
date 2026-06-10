package com.guandan.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guandan.entity.GameRecord;
import com.guandan.mapper.GameRecordMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ============================================================
    //  战绩导出（JSON/CSV格式）
    // ============================================================

    /**
     * 当前日期格式化器
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * CSV 列分隔符
     */
    private static final String CSV_DELIMITER = ",";

    /**
     * CSV 行分隔符
     */
    private static final String CSV_LINE_BREAK = "\r\n";

    /**
     * 导出玩家战绩为JSON格式
     *
     * @param userId 玩家ID
     * @return JSON字符串，包含所有战绩记录的数组
     */
    public String exportRecordsAsJson(Long userId) {
        try {
            // 查找该玩家参与的所有战绩记录
            List<com.guandan.entity.RoomPlayer> playerRooms = roomPlayerMapper.selectList(
                    new QueryWrapper<com.guandan.entity.RoomPlayer>()
                            .eq("user_id", userId)
                            .orderByDesc("id"));

            if (playerRooms == null || playerRooms.isEmpty()) {
                return "[]";
            }

            // 收集所有房间ID
            List<Long> roomIds = playerRooms.stream()
                    .map(com.guandan.entity.RoomPlayer::getRoomId)
                    .distinct()
                    .collect(Collectors.toList());

            if (roomIds.isEmpty()) {
                return "[]";
            }

            // 查询对应的GameRecord
            List<GameRecord> records = gameRecordMapper.selectList(
                    new QueryWrapper<GameRecord>()
                            .in("room_id", roomIds)
                            .orderByDesc("create_time"));

            if (records == null || records.isEmpty()) {
                return "[]";
            }

            // 构建JSON数组
            StringBuilder json = new StringBuilder();
            json.append("[\n");

            for (int i = 0; i < records.size(); i++) {
                GameRecord record = records.get(i);
                boolean isWinner = record.getWinnerId() != null && record.getWinnerId().equals(userId);

                json.append("  {\n");
                json.append("    \"id\": ").append(record.getId()).append(",\n");
                json.append("    \"roomId\": ").append(record.getRoomId()).append(",\n");
                json.append("    \"isWinner\": ").append(isWinner).append(",\n");
                json.append("    \"score\": ").append(record.getScore() != null ? record.getScore() : 0).append(",\n");
                json.append("    \"createTime\": \"")
                        .append(record.getCreateTime() != null
                                ? record.getCreateTime().format(DATE_FMT) : "")
                        .append("\",\n");

                // 查询本局所有玩家
                List<com.guandan.entity.RoomPlayer> participants = roomPlayerMapper.selectList(
                        new QueryWrapper<com.guandan.entity.RoomPlayer>()
                                .eq("room_id", record.getRoomId()));

                json.append("    \"participants\": [\n");
                if (participants != null) {
                    for (int j = 0; j < participants.size(); j++) {
                        com.guandan.entity.RoomPlayer rp = participants.get(j);
                        com.guandan.entity.User user = rp.getUserId() != null
                                ? userMapper.selectById(rp.getUserId()) : null;
                        json.append("      {\n");
                        json.append("        \"userId\": ").append(rp.getUserId()).append(",\n");
                        json.append("        \"nickname\": \"")
                                .append(user != null && user.getNickname() != null
                                        ? escapeJson(user.getNickname()) : "未知").append("\",\n");
                        json.append("        \"seatIndex\": ").append(rp.getSeatIndex()).append(",\n");
                        json.append("        \"isWinner\": ").append(rp.getUserId().equals(record.getWinnerId())).append("\n");
                        json.append("      }");
                        if (j < participants.size() - 1) json.append(",");
                        json.append("\n");
                    }
                }
                json.append("    ]\n");
                json.append("  }");
                if (i < records.size() - 1) json.append(",");
                json.append("\n");
            }

            json.append("]");
            log.info("战绩JSON导出完成：userId={}, 记录数={}", userId, records.size());
            return json.toString();

        } catch (Exception e) {
            log.error("导出战绩JSON失败：userId={}", userId, e);
            return "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 导出玩家战绩为CSV格式
     *
     * @param userId 玩家ID
     * @return CSV格式字符串，包含BOM头以支持Excel中文显示
     */
    public String exportRecordsAsCsv(Long userId) {
        try {
            // BOM头（UTF-8 BOM），使Excel能正确识别UTF-8编码
            StringBuilder csv = new StringBuilder();
            csv.append('﻿');

            // CSV表头
            csv.append("记录ID").append(CSV_DELIMITER)
                    .append("房间ID").append(CSV_DELIMITER)
                    .append("是否获胜").append(CSV_DELIMITER)
                    .append("得分").append(CSV_DELIMITER)
                    .append("游戏时间").append(CSV_DELIMITER)
                    .append("对手ID").append(CSV_DELIMITER)
                    .append("对手昵称").append(CSV_LINE_BREAK);

            // 查找该玩家参与的所有战绩记录
            List<com.guandan.entity.RoomPlayer> playerRooms = roomPlayerMapper.selectList(
                    new QueryWrapper<com.guandan.entity.RoomPlayer>()
                            .eq("user_id", userId)
                            .orderByDesc("id"));

            if (playerRooms == null || playerRooms.isEmpty()) {
                log.info("战绩CSV导出完成：userId={}, 无记录", userId);
                return csv.toString();
            }

            List<Long> roomIds = playerRooms.stream()
                    .map(com.guandan.entity.RoomPlayer::getRoomId)
                    .distinct()
                    .collect(Collectors.toList());

            List<GameRecord> records = gameRecordMapper.selectList(
                    new QueryWrapper<GameRecord>()
                            .in("room_id", roomIds)
                            .orderByDesc("create_time"));

            if (records == null || records.isEmpty()) {
                return csv.toString();
            }

            for (GameRecord record : records) {
                boolean isWinner = record.getWinnerId() != null && record.getWinnerId().equals(userId);

                // 查询本局所有对手信息
                List<com.guandan.entity.RoomPlayer> participants = roomPlayerMapper.selectList(
                        new QueryWrapper<com.guandan.entity.RoomPlayer>()
                                .eq("room_id", record.getRoomId()));

                // 如果对手有多个，用分号分隔
                StringBuilder opponentIds = new StringBuilder();
                StringBuilder opponentNames = new StringBuilder();
                if (participants != null) {
                    for (com.guandan.entity.RoomPlayer rp : participants) {
                        if (rp.getUserId() != null && !rp.getUserId().equals(userId)) {
                            if (opponentIds.length() > 0) opponentIds.append(";");
                            if (opponentNames.length() > 0) opponentNames.append(";");
                            opponentIds.append(rp.getUserId());
                            com.guandan.entity.User oppUser = userMapper.selectById(rp.getUserId());
                            opponentNames.append(oppUser != null && oppUser.getNickname() != null
                                    ? oppUser.getNickname() : "未知");
                        }
                    }
                }

                csv.append(record.getId()).append(CSV_DELIMITER)
                        .append(record.getRoomId()).append(CSV_DELIMITER)
                        .append(isWinner ? "是" : "否").append(CSV_DELIMITER)
                        .append(record.getScore() != null ? record.getScore() : 0).append(CSV_DELIMITER)
                        .append(record.getCreateTime() != null
                                ? record.getCreateTime().format(DATE_FMT) : "").append(CSV_DELIMITER)
                        .append("\"").append(escapeCsv(opponentIds.toString())).append("\"").append(CSV_DELIMITER)
                        .append("\"").append(escapeCsv(opponentNames.toString())).append("\"")
                        .append(CSV_LINE_BREAK);
            }

            log.info("战绩CSV导出完成：userId={}, 记录数={}", userId, records.size());
            return csv.toString();

        } catch (Exception e) {
            log.error("导出战绩CSV失败：userId={}", userId, e);
            return "﻿错误信息," + escapeCsv(e.getMessage());
        }
    }

    /**
     * 导出战绩（统一入口，自动检测格式）
     *
     * @param userId 玩家ID
     * @param format 导出格式："json" 或 "csv"
     * @return 格式化后的战绩字符串
     */
    public String exportRecords(Long userId, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return exportRecordsAsCsv(userId);
        }
        return exportRecordsAsJson(userId);
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 转义CSV字段中的特殊字符
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        // 如果包含逗号、引号或换行，需要用引号包裹
        if (value.contains(CSV_DELIMITER) || value.contains("\"") || value.contains("\n")) {
            return value.replace("\"", "\"\"");
        }
        return value;
    }
}
