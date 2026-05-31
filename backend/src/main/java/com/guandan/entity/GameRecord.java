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
 *
 * <p><b>测试验证点：</b>
 * <ul>
 *   <li>[TC-GR-001] 创建 GameRecord：所有必填字段非空</li>
 *   <li>[TC-GR-002] roomId 对应合法房间记录</li>
 *   <li>[TC-GR-003] winnerId 必须是房间中的玩家</li>
 *   <li>[TC-GR-004] score 允许 0（平局兜底）</li>
 *   <li>[TC-GR-005] createTime 自动填充当前时间</li>
 *   <li>[TC-GR-006] 异常路径：null winnerId → 插入成功（预留）</li>
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

    @TableField("create_time")
    private LocalDateTime createTime;
}
