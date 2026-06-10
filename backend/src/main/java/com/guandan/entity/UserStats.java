package com.guandan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_stats")
public class UserStats {

    @TableId(type = IdType.INPUT)
    @TableField("user_id")
    private Long userId;

    @TableField("total_games")
    private Integer totalGames;

    @TableField("win_games")
    private Integer winGames;

    @TableField("level_current")
    private Integer levelCurrent;

    @TableField("max_bomb_rank")
    private String maxBombRank;
}
