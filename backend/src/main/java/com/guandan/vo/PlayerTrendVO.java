package com.guandan.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 玩家趋势统计 VO
 * 包含连胜/连败趋势统计和历史对局热力图数据
 */
@Data
public class PlayerTrendVO {

    /** 当前连胜/连败次数（正数=连胜，负数=连败，0=无趋势） */
    private int streakCount;

    /** 连胜/连败类型：win_streak / lose_streak / none */
    private String streakType;

    /** 最大连胜次数（历史） */
    private int maxWinStreak;

    /** 最大连败次数（历史） */
    private int maxLoseStreak;

    /** 最近 N 局胜负序列（true=胜，false=负） */
    private List<Boolean> recentResults;

    /** 近 7 天每日对局统计 { "YYYY-MM-DD": { wins, losses, total } } */
    private List<Map<String, Object>> dailyStats;

    /** 近 7 天每日游戏时间分布（小时） */
    private List<Map<String, Object>> hourlyDistribution;
}
