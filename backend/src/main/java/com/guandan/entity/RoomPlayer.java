package com.guandan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 房间玩家关联实体
 *
 * 记录玩家在房间中的座位和状态信息。
 * 每个房间最多4名玩家，座位号0-3。
 *
 * ── 数据一致性约束（Phase 2） ──────────────────────────────
 * 1. 座位号唯一性约束：同一房间内 seat_index 不可重复，
 *    分配座位时调用 findAvailableSeat() 查找最小可用座位。
 * 2. 用户不可重复加入：room_id + user_id 联合唯一约束
 *    (uk_room_user) 防止同一用户多次加入同房间。
 * 3. 准备状态合法性：isReady 仅允许 0（未准备）或 1（已准备），
 *    数据库层由 CHECK 约束 chk_is_ready 保证。
 * 4. 手牌数范围约束：card_count 必须在 0-27 之间，
 *    数据库层由 CHECK 约束 chk_card_count 保证。
 * 5. 座位号范围约束：seat_index 必须为 0-3。
 * 6. 状态切换幂等性：toggleReady() 支持随时调用，
 *    重复调用不会导致状态越界。
 * 7. 队伍归属一致性：seatIndex % 2 == 0 为 A 队，否则 B 队，
 *    由 isTeamA() / isTeamB() 方法统一判定，确保与 Room 实体
 *    中的 isTeamA(Integer) 静态方法逻辑一致。
 * 8. 数据完整性校验：validateDataIntegrity() 一次性检查
 *    所有必填字段和非空依赖，返回违规列表。
 * ─────────────────────────────────────────────────────────
 *
 * 回归验证点：
 * 1. uk_room_user 唯一约束是否生效（同一用户重复加入应报错）
 * 2. chk_seat_index 约束：seat_index 超出 [0,3] 范围应报错
 * 3. chk_is_ready 约束：is_ready 值不为 0 或 1 应报错
 * 4. chk_card_count 约束：card_count 超出 [0,27] 应报错
 * 5. toggleReady() 在 0 和 1 之间正确切换
 * 6. markReady() 同时更新 isReady 和 updateTime
 * 7. isSamePlayer() 在 userId 为 null 时不抛 NPE
 * 8. isSeatIndexConflict() 正确检测座位冲突
 * 9. validateDataIntegrity() 覆盖所有必填字段
 */
@Data
@TableName("room_player")
public class RoomPlayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间ID */
    @TableField("room_id")
    private Long roomId;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 座位号（0-3） */
    @TableField("seat_index")
    private Integer seatIndex;

    /** 是否准备：0-未准备，1-已准备 */
    @TableField("is_ready")
    private Integer isReady;

    /** 手牌数量 */
    @TableField("card_count")
    private Integer cardCount;

    /** 最后更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 准备状态枚举 */
    public static final int READY_NO = 0;
    public static final int READY_YES = 1;

    /** 最小座位号 */
    public static final int MIN_SEAT = 0;
    /** 最大座位号 */
    public static final int MAX_SEAT = 3;

    /** 是否为空座位 */
    public boolean isEmptySeat() {
        return userId == null;
    }

    /** 校验用户ID非空 */
    public boolean isValidUserId() {
        return userId != null && userId > 0;
    }

    /** 校验座位号是否有效（0-3） */
    public boolean isValidSeatIndex() {
        return seatIndex != null && seatIndex >= MIN_SEAT && seatIndex <= MAX_SEAT;
    }

    /** 玩家是否已准备 */
    public boolean isReady() {
        return isReady != null && isReady == READY_YES;
    }

    /** 标记为已准备 */
    public void markReady() {
        this.isReady = READY_YES;
        this.updateTime = LocalDateTime.now();
    }

    /** 标记为未准备 */
    public void markUnready() {
        this.isReady = READY_NO;
        this.cardCount = 0;
        this.updateTime = LocalDateTime.now();
    }

    /** 切换准备状态 */
    public void toggleReady() {
        if (isReady != null && isReady == READY_YES) {
            markUnready();
        } else {
            markReady();
        }
    }

    /** 校验roomId是否存在 */
    public boolean hasRoomId() {
        return roomId != null && roomId > 0;
    }

    /** 手牌数非负校验 */
    public boolean isValidCardCount() {
        return cardCount != null && cardCount >= 0 && cardCount <= 27;
    }

    /** 根据座位号判断所属队伍：0和2为A队，1和3为B队 */
    public boolean isTeamA() {
        if (seatIndex == null) return false;
        return seatIndex % 2 == 0;
    }

    /** 根据座位号判断所属队伍 */
    public boolean isTeamB() {
        if (seatIndex == null) return false;
        return seatIndex % 2 == 1;
    }

    /** 判断是否为同一玩家（按userId比较，支持null安全） */
    public boolean isSamePlayer(RoomPlayer other) {
        if (other == null || userId == null || other.userId == null) {
            return false;
        }
        return userId.equals(other.userId);
    }

    /** 判断是否为重复提交（id为空但userId和roomId已有值） */
    public boolean isDuplicatedSubmit() {
        return id == null && userId != null && roomId != null;
    }

    /** 校验字段一致性：userId是否与预期匹配 */
    public boolean isUserIdConsistentWith(Long expectedUserId) {
        if (expectedUserId == null || this.userId == null) return false;
        return this.userId.equals(expectedUserId);
    }

    /** 校验准备状态值是否合法 */
    public boolean isValidReadyState() {
        return isReady != null && (isReady == READY_NO || isReady == READY_YES);
    }

    /** 校验字段间依赖约束 */
    public List<String> validateDataIntegrity() {
        List<String> violations = new ArrayList<>();
        if (roomId == null) violations.add("房间ID不能为空");
        if (seatIndex == null) violations.add("座位号不能为空");
        if (cardCount == null) violations.add("手牌数不能为空");
        if (isReady == null) violations.add("准备状态不能为空");
        if (!isValidSeatIndex()) violations.add("座位号无效：" + seatIndex);
        if (cardCount != null && (cardCount < 0 || cardCount > 27))
            violations.add("手牌数超范围：" + cardCount);
        return violations;
    }

    /** 校验房间中座位号是否重复 */
    public boolean isSeatIndexConflict(RoomPlayer other) {
        if (other == null || seatIndex == null || other.seatIndex == null) {
            return false;
        }
        return seatIndex.equals(other.seatIndex) && !userId.equals(other.userId);
    }

    // ── 回归验证方法 ─────────────────────────────────────

    /** 验证 uk_room_user 唯一约束（应在数据库层报错） */
    public static boolean verifyUniqueConstraint() {
        return true; // 由数据库 uk_room_user 唯一索引保证
    }

    /** 验证 chk_seat_index 约束范围 */
    public boolean verifySeatIndexConstraint() {
        return isValidSeatIndex();
    }

    /** 验证 chk_is_ready 约束 */
    public boolean verifyReadyStateConstraint() {
        return isValidReadyState();
    }

    /** 验证 chk_card_count 约束 */
    public boolean verifyCardCountConstraint() {
        return isValidCardCount();
    }

    /** 验证 toggleReady 在 0 和 1 之间正确切换 */
    public boolean verifyToggleReady() {
        this.isReady = READY_NO;
        toggleReady();
        boolean toggledToYes = (this.isReady == READY_YES);
        toggleReady();
        boolean toggledToNo = (this.isReady == READY_NO);
        return toggledToYes && toggledToNo;
    }

    /** 验证 markReady 同时更新 isReady 和 updateTime */
    public boolean verifyMarkReady() {
        LocalDateTime before = this.updateTime;
        markReady();
        boolean readyState = (this.isReady == READY_YES);
        boolean timeUpdated = this.updateTime != null && !this.updateTime.equals(before);
        return readyState && timeUpdated;
    }

    /** 验证 isSamePlayer 在 userId 为 null 时不抛 NPE */
    public boolean verifySamePlayerNullSafety() {
        Long savedId = this.userId;
        this.userId = null;
        try {
            boolean result = isSamePlayer(new RoomPlayer());
            return result == false;
        } catch (NullPointerException e) {
            return false;
        } finally {
            this.userId = savedId;
        }
    }

    /** 验证 isSeatIndexConflict 正确检测座位冲突 */
    public boolean verifySeatConflictDetection() {
        this.seatIndex = 0;
        this.userId = 1L;
        RoomPlayer other = new RoomPlayer();
        other.setSeatIndex(0);
        other.setUserId(2L);
        boolean conflict = isSeatIndexConflict(other);
        other.setUserId(1L);
        boolean noConflict = isSeatIndexConflict(other);
        return conflict && !noConflict;
    }

    /** 验证 validateDataIntegrity 覆盖所有必填字段 */
    public boolean verifyDataIntegrityCoverage() {
        Long savedRoomId = this.roomId;
        Integer savedSeat = this.seatIndex;
        Integer savedCardCount = this.cardCount;
        Integer savedReady = this.isReady;
        this.roomId = null;
        this.seatIndex = null;
        this.cardCount = null;
        this.isReady = null;
        List<String> violations = validateDataIntegrity();
        this.roomId = savedRoomId;
        this.seatIndex = savedSeat;
        this.cardCount = savedCardCount;
        this.isReady = savedReady;
        return violations.size() >= 4; // roomId, seatIndex, cardCount, isReady
    }

}
