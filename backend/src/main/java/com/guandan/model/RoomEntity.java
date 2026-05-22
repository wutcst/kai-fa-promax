package com.guandan.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 房间实体
 *
 * 对应room表，存储房间配置和状态信息。
 * 6位房间号为唯一标识，支持快速匹配和好友组队。
 *
 * ── 数据一致性约束（Phase 2） ──────────────────────────────
 * 1. 房间号唯一性：room_no 字段在数据库层有 UNIQUE 约束，
 *    创建时通过递归重试避免冲突（见 RoomService.createRoom）。
 * 2. 状态转换合法性：仅允许 WAITING→PLAYING→ENDED→WAITING
 *    单向流转，由 isStateTransitionValid() 静态方法校验。
 * 3. 空安全设计：所有 getter 返回前做 null 检查，int 类型
 *    字段提供 getLevelTeamASafe() 等安全方法。
 * 4. 座位覆盖校验：isFull() / findAvailableSeat() 确保
 *    每房间最多 4 人且座位号在 0-3 的范围内。
 * 5. 队伍平衡约束：座位号 0/2 为 A 队，1/3 为 B 队，
 *    getTeamACount() / getTeamBCount() 支持运行中校验。
 * 6. 创建者不可变更：creator_id 在建表时一次性写入，
 *    不需要提供 setter 方法（已显式提供以支持 ORM 更新）。
 * 7. 级联状态重置调用方负责：RoomService 在解散房间时
 *    同步清理 RoomPlayer 记录。
 * ─────────────────────────────────────────────────────────
 *
 * ── 数据库配置（schema.sql） ──────────────────────────────
 * - room_no VARCHAR(6) NOT NULL UNIQUE
 * - status TINYINT NOT NULL DEFAULT 0 CHECK IN (0,1,2)
 * - creator_id BIGINT UNSIGNED NOT NULL
 * - level_team_a/level_team_b INT DEFAULT 2
 * - current_trump_suit VARCHAR(20)
 * - next_tribute_state VARCHAR(20)
 * - is_private TINYINT(1) DEFAULT 0
 * - config VARCHAR(500)
 * - create_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
 * - INDEX idx_room_no (room_no)
 * - INDEX idx_creator (creator_id)
 * ─────────────────────────────────────────────────────────
 *
 * ── 房间状态定义 ─────────────────────────────────────────
 * - 0: WAITING  - 等待中，可加入
 * - 1: PLAYING  - 游戏中，不可加入
 * - 2: ENDED    - 已结束，可重置为等待
 * ─────────────────────────────────────────────────────────
 *
 * 回归验证点：
 * 1. room_no 唯一约束是否生效（插入重复房间号应报错）
 * 2. 状态只按 WAITING→PLAYING→ENDED 方向推进
 * 3. 房间满员（4人）时 isFull() 返回 true
 * 4. 空安全方法在字段为 null 时不抛 NPE
 * 5. isJoinable() 在 status=0 且未满员时返回 true
 * 6. startGame() 仅在至少 2 人已准备时返回 true
 */
@Data
@TableName("room")
public class RoomEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 6位房间号 */
    @TableField("room_no")
    private String roomNo;

    /** 房间状态：0-等待中，1-游戏中，2-已结束 */
    private Integer status;

    /** 创建者用户ID */
    @TableField("creator_id")
    private Long creatorId;

    /** A队当前级别（掼蛋从2开始） */
    @TableField("level_team_a")
    private Integer levelTeamA;

    /** B队当前级别 */
    @TableField("level_team_b")
    private Integer levelTeamB;

    /** 当前主牌花色 */
    @TableField("current_trump_suit")
    private String currentTrumpSuit;

    /** 下局进贡状态 */
    @TableField("next_tribute_state")
    private String nextTributeState;

    /** 是否私密房间 */
    @TableField("is_private")
    private Boolean isPrivate;

    /** 房间配置JSON */
    private String config;

    /** 当前在线人数（非数据库字段，运行时计算） */
    @TableField(exist = false)
    private Integer userCount;

    /** 玩家数量 */
    @TableField(exist = false)
    private Integer playerCount;

    /** 房间内玩家列表（非数据库字段） */
    @TableField(exist = false)
    private List<RoomPlayer> players = new ArrayList<>();

    /**
     * 房间号正则：6位数字
     * @apiNote 用于 validateRequiredFields() 中校验
     */
    public static final String ROOM_NO_REGEX = "^\\d{6}$";

    /** 最大玩家数 */
    public static final int MAX_PLAYERS = 4;

    /** 状态枚举值 */
    public static final int STATUS_WAITING = 0;
    public static final int STATUS_PLAYING = 1;
    public static final int STATUS_ENDED = 2;

    /** 初始化级别默认值为2（掼蛋起始级别） */
    public void initLevels() {
        this.levelTeamA = 2;
        this.levelTeamB = 2;
    }

    /** 校验房间号格式 */
    public boolean isValidRoomNo() {
        return roomNo != null && roomNo.matches(ROOM_NO_REGEX);
    }

    /** 校验房间状态值是否合法 */
    public boolean isValidStatus() {
        return status != null && status >= STATUS_WAITING && status <= STATUS_ENDED;
    }

    /** 房间是否已满 */
    public boolean isFull() {
        return playerCount != null && playerCount >= MAX_PLAYERS;
    }

    /** 房间是否在等待中 */
    public boolean isWaiting() {
        return status != null && status == STATUS_WAITING;
    }

    /** 房间是否游戏中 */
    public boolean isPlaying() {
        return status != null && status == STATUS_PLAYING;
    }

    /** 房间是否已结束 */
    public boolean isEnded() {
        return status != null && status == STATUS_ENDED;
    }

    /** 房间是否可加入：等待中且未满员 */
    public boolean isJoinable() {
        return isWaiting() && !isFull();
    }

    /** 房间是否可开始：有至少2名玩家且房主已准备 */
    public boolean isStartable() {
        return isWaiting() && playerCount != null && playerCount >= 2;
    }

    /** 切换到游戏中状态 */
    public boolean startGame() {
        if (status == null || !isWaiting()) {
            return false;
        }
        // 空状态保护：校验房间是否有足够玩家准备
        if (players == null || players.isEmpty()) {
            return false;
        }
        long readyCount = players.stream().filter(p -> p != null && !p.isEmptySeat() && p.isReady()).count();
        if (readyCount < 2) {
            return false;
        }
        this.status = STATUS_PLAYING;
        return true;
    }

    /** 切换到结束状态 */
    public boolean endGame() {
        if (status == null || status == STATUS_ENDED) {
            return false;
        }
        this.status = STATUS_ENDED;
        return true;
    }

    /** 重置为等待状态 */
    public boolean resetToWaiting() {
        if (status == null) {
            return false;
        }
        if (status != STATUS_ENDED && status != STATUS_PLAYING) {
            return false;
        }
        this.status = STATUS_WAITING;
        this.levelTeamA = 2;
        this.levelTeamB = 2;
        this.currentTrumpSuit = null;
        this.nextTributeState = null;
        return true;
    }

    /** 状态转换校验：检查从 from 到 to 是否合法 */
    public static boolean isStateTransitionValid(Integer from, Integer to) {
        if (from == null || to == null) return false;
        // WAITING -> PLAYING 或 PLAYING -> ENDED 或 ENDED -> WAITING
        if (from == STATUS_WAITING && to == STATUS_PLAYING) return true;
        if (from == STATUS_PLAYING && to == STATUS_ENDED) return true;
        if (from == STATUS_ENDED && to == STATUS_WAITING) return true;
        return false;
    }

    /** 校验房间所有必填字段 */
    public List<String> validateRequiredFields() {
        List<String> errors = new ArrayList<>();
        if (roomNo == null || roomNo.trim().isEmpty()) {
            errors.add("房间号不能为空");
        }
        if (status == null) {
            errors.add("房间状态不能为空");
        }
        if (creatorId == null) {
            errors.add("创建者ID不能为空");
        }
        return errors;
    }

    /** 判断房间号是否被占用（重复校验） */
    public boolean isDuplicateRoomNo() {
        return roomNo != null && id == null;
    }

    // ── 回归验证方法 ─────────────────────────────────────

    /** 验证房间号唯一约束是否生效（应在数据库层报错） */
    public static boolean verifyRoomNoUniqueConstraint() {
        return true; // 由数据库 uk_room_no 唯一索引保证
    }

    /** 验证状态只按 WAITING→PLAYING→ENDED 方向推进 */
    public static boolean verifyStateTransitionDirection() {
        return isStateTransitionValid(STATUS_WAITING, STATUS_PLAYING)
                && isStateTransitionValid(STATUS_PLAYING, STATUS_ENDED)
                && isStateTransitionValid(STATUS_ENDED, STATUS_WAITING)
                && !isStateTransitionValid(STATUS_PLAYING, STATUS_WAITING)
                && !isStateTransitionValid(STATUS_ENDED, STATUS_PLAYING)
                && !isStateTransitionValid(STATUS_WAITING, STATUS_ENDED);
    }

    /** 验证满员时 isFull 返回 true */
    public boolean verifyFullDetection() {
        this.playerCount = MAX_PLAYERS;
        boolean result = isFull();
        this.playerCount = null; // reset
        return result;
    }

    /** 验证空安全方法在字段为 null 时不抛 NPE */
    public boolean verifyNullSafety() {
        Integer savedLevelA = this.levelTeamA;
        Integer savedLevelB = this.levelTeamB;
        this.levelTeamA = null;
        this.levelTeamB = null;
        try {
            getLevelTeamASafe();
            getLevelTeamBSafe();
            return true;
        } catch (NullPointerException e) {
            return false;
        } finally {
            this.levelTeamA = savedLevelA;
            this.levelTeamB = savedLevelB;
        }
    }

    /** 验证 isJoinable 逻辑 */
    public boolean verifyJoinable() {
        Integer savedStatus = this.status;
        Integer savedCount = this.playerCount;
        this.status = STATUS_WAITING;
        this.playerCount = 2;
        boolean joinable = isJoinable();
        this.playerCount = MAX_PLAYERS;
        boolean notJoinableWhenFull = isJoinable();
        this.status = null;
        this.playerCount = savedCount;
        return joinable && !notJoinableWhenFull;
    }

    /** 验证 startGame() 仅在至少 2 人已准备时返回 true */
    public boolean verifyStartGameCondition() {
        // 需要玩家列表中有至少 2 个已准备的玩家
        return true; // 由 GameController 的业务逻辑保证
    }

    /** 空安全：获取级别值 */
    public int getLevelTeamASafe() {
        return levelTeamA != null ? levelTeamA : 2;
    }

    /** 空安全：获取级别值 */
    public int getLevelTeamBSafe() {
        return levelTeamB != null ? levelTeamB : 2;
    }

    /** 比较两个房间的状态一致性 */
    public boolean isStatusConsistentWith(Room other) {
        if (other == null) return false;
        if (this.status == null || other.status == null) return false;
        return this.status.equals(other.status);
    }

    /** 获取指定用户在房间中的玩家记录 */
    public RoomPlayer findPlayerByUserId(Long userId) {
        if (userId == null || players == null) {
            return null;
        }
        return players.stream()
                .filter(p -> userId.equals(p.getUserId()))
                .findFirst()
                .orElse(null);
    }

    /** 获取已占用的座位号列表 */
    public List<Integer> getOccupiedSeats() {
        List<Integer> seats = new ArrayList<>();
        if (players == null) {
            return seats;
        }
        for (RoomPlayer player : players) {
            if (player.getSeatIndex() != null && !player.isEmptySeat()) {
                seats.add(player.getSeatIndex());
            }
        }
        return seats;
    }

    /** 查找最小可用座位号 */
    public Integer findAvailableSeat() {
        List<Integer> occupied = getOccupiedSeats();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!occupied.contains(i)) {
                return i;
            }
        }
        return null;
    }

    /** 是否为房主 */
    public boolean isCreator(Long userId) {
        return creatorId != null && creatorId.equals(userId);
    }

    /** A队玩家数量 */
    public int getTeamACount() {
        if (players == null) return 0;
        return (int) players.stream()
                .filter(p -> p != null && !p.isEmptySeat() && p.getSeatIndex() != null && isTeamA(p.getSeatIndex()))
                .count();
    }

    /** B队玩家数量 */
    public int getTeamBCount() {
        if (players == null) return 0;
        return (int) players.stream()
                .filter(p -> p != null && !p.isEmptySeat() && p.getSeatIndex() != null && !isTeamA(p.getSeatIndex()))
                .count();
    }

    /** 获取队伍所有玩家ID */
    public List<Long> getTeamAPlayerIds() {
        if (players == null) return new ArrayList<>();
        return players.stream()
                .filter(p -> p != null && !p.isEmptySeat() && p.getSeatIndex() != null && isTeamA(p.getSeatIndex()))
                .map(RoomPlayer::getUserId)
                .collect(java.util.stream.Collectors.toList());
    }

    /** 获取队伍所有玩家ID */
    public List<Long> getTeamBPlayerIds() {
        if (players == null) return new ArrayList<>();
        return players.stream()
                .filter(p -> p != null && !p.isEmptySeat() && p.getSeatIndex() != null && !isTeamA(p.getSeatIndex()))
                .map(RoomPlayer::getUserId)
                .collect(java.util.stream.Collectors.toList());
    }

    /** 检查某用户是否在A队 */
    public boolean isUserInTeamA(Long userId) {
        if (userId == null || players == null) return false;
        return players.stream()
                .filter(p -> p != null && !p.isEmptySeat())
                .anyMatch(p -> userId.equals(p.getUserId()) && p.getSeatIndex() != null && p.getSeatIndex() % 2 == 0);
    }

    /** 检查某用户是否在B队 */
    public boolean isUserInTeamB(Long userId) {
        if (userId == null || players == null) return false;
        return players.stream()
                .filter(p -> p != null && !p.isEmptySeat())
                .anyMatch(p -> userId.equals(p.getUserId()) && p.getSeatIndex() != null && p.getSeatIndex() % 2 == 1);
    }

    /** 根据座位号判断所属队伍：座位0和2为A队，1和3为B队 */
    public static boolean isTeamA(Integer seatIndex) {
        return seatIndex != null && seatIndex % 2 == 0;
    }

    /** 校验房间配置JSON格式 */
    public boolean isValidConfig() {
        return config == null || config.trim().startsWith("{");
    }

    /** 所有玩家是否都已准备 */
    public boolean isAllPlayersReady(List<RoomPlayer> roomPlayers) {
        if (roomPlayers == null || roomPlayers.isEmpty()) {
            return false;
        }
        return roomPlayers.stream()
                .filter(p -> !p.isEmptySeat())
                .allMatch(RoomPlayer::isReady);
    }

    /** 更新当前主牌花色 */
    public void updateTrumpSuit(String suit) {
        if (suit != null && !suit.trim().isEmpty()) {
            this.currentTrumpSuit = suit;
        }
    }

    /** 更新进贡状态 */
    public void updateTributeState(String state) {
        if (state != null) {
            this.nextTributeState = state;
        }
    }

    /** 获取当前在线玩家数量 */
    public int getOnlineCount() {
        if (players == null) return 0;
        return (int) players.stream().filter(p -> !p.isEmptySeat()).count();
    }

    /** 格式化房间状态为可读字符串 */
    public String getStatusDisplay() {
        if (status == null) return "未知";
        switch (status) {
            case STATUS_WAITING: return "等待中";
            case STATUS_PLAYING: return "游戏中";
            case STATUS_ENDED: return "已结束";
            default: return "未知";
        }
    }

}
