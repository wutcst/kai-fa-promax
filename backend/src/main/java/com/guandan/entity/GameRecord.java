package com.guandan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏战绩记录实体
 *
 * <p>保存每局游戏的胜负记录，用于玩家战绩查询和统计。
 * <p>支持游戏回放存储，按回合（round）分段查询。
 *
 * <p><b>测试验证点：</b>
 * <ul>
 *   <li>[TC-GR-001] 创建 GameRecord：所有必填字段非空</li>
 *   <li>[TC-GR-002] roomId 对应合法房间记录</li>
 *   <li>[TC-GR-003] winnerId 必须是房间中的玩家</li>
 *   <li>[TC-GR-004] score 允许 0（平局兜底）</li>
 *   <li>[TC-GR-005] createTime 自动填充当前时间</li>
 *   <li>[TC-GR-006] 异常路径：null winnerId → 插入成功（预留）</li>
 *   <li>[TC-GR-007] totalRounds 记录总局数，用于回放分页</li>
 *   <li>[TC-GR-008] roundData 存储 JSON 格式回合详情</li>
 * </ul>
 */
@Data
@TableName("game_record")
public class GameRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("room_id")
    private Long roomId;

    @TableField("winner_id")
    private Long winnerId;

    private Integer score;

    /** 游戏总局数（用于回放分页） */
    @TableField("total_rounds")
    private Integer totalRounds;

    /** 当前保存的回合序号（从1开始） */
    @TableField("current_round")
    private Integer currentRound;

    /** 回放数据（JSON格式，存储每回合的牌型和操作） */
    @TableField("round_data")
    private String roundData;

    @TableField("create_time")
    private LocalDateTime createTime;

    /** 回放记录更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
