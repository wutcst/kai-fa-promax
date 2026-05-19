package com.guandan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
}
