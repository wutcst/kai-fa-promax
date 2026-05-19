package com.guandan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 房间实体
 *
 * 对应room表，存储房间配置和状态信息。
 * 6位房间号为唯一标识，支持快速匹配和好友组队。
 */
@Data
@TableName("room")
public class Room {

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

    /** 初始化级别默认值为2（掼蛋起始级别） */
    public void initLevels() {
        this.levelTeamA = 2;
        this.levelTeamB = 2;
    }

    public boolean isFull() {
        return playerCount != null && playerCount >= 4;
    }

    public boolean isWaiting() {
        return status != null && status == 0;
    }
}
