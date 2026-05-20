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

}
