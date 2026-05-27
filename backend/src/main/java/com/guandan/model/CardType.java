package com.guandan.model;

/**
 * 卡牌类型枚举
 * 定义掼蛋/扑克的基础牌型
 */
public enum CardType {
    /**
     * 单张
     */
    SINGLE,

    /**
     * 对子（两张相同点数）
     */
    PAIR,

    /**
     * 三张（三张相同点数）
     */
    TRIPLET,

    /**
     * 顺子（至少5张连续点数）
     */
    STRAIGHT,

    /**
     * 三带二（三张相同点数 + 一对）
     */
    TRIPLET_WITH_TWO,

    /**
     * 三连对（至少3对连续点数，每对两张相同）
     */
    PAIR_STRAIGHT,

    /**
     * 三顺（至少2组连续三张，每组三张相同点数）
     */
    TRIPLET_STRAIGHT,

    /**
     * 同花顺（至少5张连续点数且同花色）
     */
    FLUSH_STRAIGHT,

    /**
     * 4-5张炸弹
     */
    SMALL_BOMB,

    /**
     * 6-10张炸弹
     */
    BIG_BOMB,

    /**
     * 王炸
     */
    ROCKET,

    /**
     * 未知/不合法牌型
     */
    UNKNOWN;

    /**
     * 判断是否为炸弹类型
     * @return 如果是炸弹返回true
     */
    public boolean isBomb() {
        return this == SMALL_BOMB || this == BIG_BOMB || this == ROCKET;
    }
}
