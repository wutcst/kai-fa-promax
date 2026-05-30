package com.guandan.model;

/**
 * 卡牌类型枚举
 * 定义掼蛋/扑克的基础牌型
 *
 * <p><b>牌型体系：</b>
 * <ul>
 *   <li>SINGLE — 单张（1张）</li>
 *   <li>PAIR — 对子（2张同点数）</li>
 *   <li>TRIPLET — 三张（3张同点数）</li>
 *   <li>STRAIGHT — 顺子（至少5张连续点数，不含2和王）</li>
 *   <li>TRIPLET_WITH_TWO — 三带二（3+2）</li>
 *   <li>PAIR_STRAIGHT — 三连对（至少3对连续点数）</li>
 *   <li>TRIPLET_STRAIGHT — 三顺/钢板（至少2组连续三张）</li>
 *   <li>FLUSH_STRAIGHT — 同花顺（5张同花色连续点数）</li>
 *   <li>SMALL_BOMB — 4-5张炸弹</li>
 *   <li>BIG_BOMB — 6-10张炸弹</li>
 *   <li>ROCKET — 王炸（大小王）</li>
 *   <li>UNKNOWN — 未知/不合法牌型</li>
 * </ul>
 *
 * <p><b>大小比较规则：</b>
 * <ul>
 *   <li>同牌型：按牌值比较点数</li>
 *   <li>炸弹可以管任何非炸弹牌型</li>
 *   <li>火箭（王炸）最大</li>
 *   <li>同花顺大于普通顺子和三带二</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>无法识别的牌型 → UNKNOWN</li>
 *   <li>空牌列表 → UNKNOWN</li>
 *   <li>isBomb() 对 SMALL_BOMB / BIG_BOMB / ROCKET 返回 true，其他 false</li>
 * </ul>
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

    // ============================================================
    //  测试验证点 — 牌型枚举完整性回归
    // ============================================================
    // [CardType-1] SINGLE 枚举存在 -> 单张牌型
    // [CardType-2] PAIR 枚举存在 -> 对子牌型
    // [CardType-3] TRIPLET 枚举存在 -> 三张牌型
    // [CardType-4] STRAIGHT 枚举存在 -> 顺子牌型
    // [CardType-5] TRIPLET_WITH_TWO 枚举存在 -> 三带二牌型
    // [CardType-6] FLUSH_STRAIGHT 枚举存在 -> 同花顺牌型
    // [CardType-7] SMALL_BOMB 枚举存在 -> 4-5张炸弹牌型
    // [CardType-8] BIG_BOMB 枚举存在 -> 6-10张炸弹牌型
    // [CardType-9] ROCKET 枚举存在 -> 王炸牌型
    // [CardType-10] UNKNOWN 枚举存在 -> 未知/不合法牌型
    // [CardType-11] isBomb() 对 SMALL_BOMB 返回 true
    // [CardType-12] isBomb() 对 BIG_BOMB 返回 true
    // [CardType-13] isBomb() 对 ROCKET 返回 true
    // [CardType-14] isBomb() 对 SINGLE/PAIR/TRIPLET 返回 false
    // [CardType-15] isBomb() 对 UNKNOWN 返回 false
}
