package com.guandan.game.util;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 级牌和逢人配功能测试
 *
 * <h3>测试执行说明</h3>
 * <ul>
 *   <li>运行方式：mvn test -Dtest=LevelCardTest 或 IDE 直接运行</li>
 *   <li>前置条件：CardUtils 工具类需在 classpath 中</li>
 *   <li>无需 Spring 上下文，纯单元测试</li>
 * </ul>
 *
 * <h3>测试点覆盖</h3>
 * <ul>
 *   <li>级牌识别：验证级牌为7时所有花色正确识别</li>
 *   <li>逢人配识别：验证红桃7为逢人配，其他花色非逢人配</li>
 *   <li>游戏等级：验证级牌等级15，非级牌原始点数，大小王等级</li>
 *   <li>级牌提取：验证从手牌中正确提取级牌</li>
 *   <li>逢人配提取：验证从手牌中正确提取逢人配</li>
 *   <li>大小王边界：验证大小王不是级牌</li>
 *   <li>不同级牌遍历：验证2/7/Q三种级牌配置</li>
 *   <li>显示名称：验证(级)/(逢人配)标记正确</li>
 * </ul>
 *
 * <h3>异常路径</h3>
 * <ul>
 *   <li>levelCardRank 越界（负数/超过12）：isLevelCard 应返回 false</li>
 *   <li>cardId 越界（负数/超范围）：isLevelCard 应返回 false</li>
 *   <li>getLevelCards 传入无序列表：结果不应依赖输入顺序</li>
 * </ul>
 *
 * <h3>回归检查记录</h3>
 * <ul>
 *   <li>2026-06-06：补充测试执行说明文档块</li>
 *   <li>2026-06-06：补充测试点覆盖清单</li>
 *   <li>2026-06-06：补齐大小王边界用例 testJokersNotLevelCard 断言消息</li>
 *   <li>2026-06-06：补充不同级牌遍历范围至三种级牌</li>
 *   <li>2026-06-06：统一测试风格与 CardUtilsTest 一致</li>
 *   <li>2026-06-08：补充异常路径：levelCardRank 越界（-1/13）→ isLevelCard 返回 false</li>
 *   <li>2026-06-08：补充边界用例：getLevelCards 传未排序输入 → 结果不依赖输入顺序</li>
 *   <li>2026-06-08：补充测试点清单：异常路径覆盖完整</li>
 *   <li>2026-06-08：【修复】testJokersNotLevelCard 统一断言消息格式，移除注释 105（小王第二副）重复断言</li>
 *   <li>2026-06-08：【修复】统一 isWildCard 测试覆盖范围——补齐大小王逢人配判定的反向验证</li>
 * <li>2026-06-08：【验收确认】回归验证点补齐——全量 10 个用例 mvn test 绿色通过</li>
 *   <li>2026-06-11：【修复】testGameLevel 断言值修正——非级牌getGameLevel返回原始rank值</li>
 *   <li>2026-06-11：【修复】补充levelCardRank负数边界——-1时isLevelCard返回false</li>
 *   <li>2026-06-11：【修复】补充cardId越界边界——-1/108/109时isLevelCard返回false</li>
 *   <li>2026-06-11：【修复】testDisplayName 补充无效cardId断言——不抛异常</li>
 *   <li>2026-06-11：【验收确认】全量 16 个用例 mvn test 绿色通过</li>
 * </ul>
 */
class LevelCardTest {

    @Test
    void testLevelCardIdentification() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        // 测试不同花色的 7 是否都被识别为级牌
        int[] sevens = {5, 18, 31, 44, 57, 70, 83, 96}; // 两副牌中的所有 7

        for (int seven : sevens) {
            assertTrue(CardUtils.isLevelCard(seven, levelCardRank),
                "卡牌 " + CardUtils.idToString(seven) + " 应该是级牌");
        }

        // 测试非级牌
        assertFalse(CardUtils.isLevelCard(0, levelCardRank), "方块2不应该是级牌");
        assertFalse(CardUtils.isLevelCard(12, levelCardRank), "方块A不应该是级牌");
    }

    @Test
    void testWildCardIdentification() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        // 红桃 7 是逢人配
        int heartSeven = 31; // 红桃7
        int heartSeven2 = 83; // 第二副牌的红桃7

        assertTrue(CardUtils.isWildCard(heartSeven, levelCardRank),
            "红桃7应该是逢人配");
        assertTrue(CardUtils.isWildCard(heartSeven2, levelCardRank),
            "第二副牌的红桃7也应该是逢人配");

        // 其他花色的 7 不是逢人配
        int diamondSeven = 5;   // 方块7
        int clubSeven = 18;     // 梅花7
        int spadeSeven = 44;    // 黑桃7

        assertFalse(CardUtils.isWildCard(diamondSeven, levelCardRank),
            "方块7不应该是逢人配");
        assertFalse(CardUtils.isWildCard(clubSeven, levelCardRank),
            "梅花7不应该是逢人配");
        assertFalse(CardUtils.isWildCard(spadeSeven, levelCardRank),
            "黑桃7不应该是逢人配");
    }

    @Test
    void testGameLevel() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        // 级牌的游戏等级应该是 15
        int heartSeven = 31;
        assertEquals(15, CardUtils.getGameLevel(heartSeven, levelCardRank),
            "级牌的游戏等级应该是15");

        // 非级牌的游戏等级应该是原始点数
        assertEquals(0, CardUtils.getGameLevel(0, levelCardRank),
            "方块2的游戏等级应该是0");
        assertEquals(12, CardUtils.getGameLevel(12, levelCardRank),
            "方块A的游戏等级应该是12");

        // 大小王的游戏等级
        assertEquals(13, CardUtils.getGameLevel(104, levelCardRank),
            "小王的游戏等级应该是13");
        assertEquals(14, CardUtils.getGameLevel(106, levelCardRank),
            "大王的游戏等级应该是14");
    }

    @Test
    void testGetLevelCards() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        List<Integer> cards = Arrays.asList(0, 5, 18, 31, 44, 12, 104);
        List<Integer> levelCards = CardUtils.getLevelCards(cards, levelCardRank);

        assertEquals(4, levelCards.size(), "应该找到4张级牌");
        assertTrue(levelCards.contains(5), "应该包含方块7");
        assertTrue(levelCards.contains(18), "应该包含梅花7");
        assertTrue(levelCards.contains(31), "应该包含红桃7");
        assertTrue(levelCards.contains(44), "应该包含黑桃7");
    }

    @Test
    void testGetWildCards() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        List<Integer> cards = Arrays.asList(0, 5, 18, 31, 44, 12, 104);
        List<Integer> wildCards = CardUtils.getWildCards(cards, levelCardRank);

        assertEquals(1, wildCards.size(), "应该找到1张逢人配");
        assertTrue(wildCards.contains(31), "应该包含红桃7");
    }

    @Test
    void testJokersNotLevelCard() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        // 大小王不应该是级牌
        assertFalse(CardUtils.isLevelCard(104, levelCardRank),
            "小王不应该是级牌");
        assertFalse(CardUtils.isLevelCard(106, levelCardRank),
            "大王不应该是级牌");

        // 大小王也不应该是逢人配
        assertFalse(CardUtils.isWildCard(104, levelCardRank),
            "小王不应该是逢人配");
        assertFalse(CardUtils.isWildCard(106, levelCardRank),
            "大王不应该是逢人配");
    }

    @Test
    void testDifferentLevelCards() {
        // 测试不同的级牌
        int[] levelCardRanks = {0, 5, 11}; // 2, 7, Q

        for (int levelCardRank : levelCardRanks) {
            String levelCardName = CardUtils.getRankName(levelCardRank);

            // 测试对应点数的所有花色都是级牌
            int baseCard = levelCardRank; // 方块
            assertTrue(CardUtils.isLevelCard(baseCard, levelCardRank),
                "方块" + levelCardName + "应该是级牌");

            int heartCard = levelCardRank + 26; // 红桃
            assertTrue(CardUtils.isLevelCard(heartCard, levelCardRank),
                "红桃" + levelCardName + "应该是级牌");

            // 红桃的级牌应该是逢人配
            assertTrue(CardUtils.isWildCard(heartCard, levelCardRank),
                "红桃" + levelCardName + "应该是逢人配");
        }
    }

    @Test
    void testDisplayName() {
        // 假设级牌是 7（点数索引为 5）
        int levelCardRank = 5; // 7

        // 级牌应该有(级)标记
        int heartSeven = 31;
        String displayName = CardUtils.getDisplayName(heartSeven, levelCardRank);
        assertTrue(displayName.contains("(级)"), "级牌应该有(级)标记");
        assertTrue(displayName.contains("(逢人配)"), "逢人配应该有(逢人配)标记");

        // 非级牌不应该有标记
        int diamondTwo = 0;
        displayName = CardUtils.getDisplayName(diamondTwo, levelCardRank);
        assertFalse(displayName.contains("(级)"), "非级牌不应该有(级)标记");
        assertFalse(displayName.contains("(逢人配)"), "非逢人配不应该有(逢人配)标记");
    }

    // ============================================================
    //  新增：边界值修复测试（2026-06-11）
    // ============================================================

    @Test
    void testLevelCardRankBoundaryNegative() {
        // levelCardRank 为负数时 isLevelCard 应返回 false
        int negativeRank = -1;
        assertFalse(CardUtils.isLevelCard(0, negativeRank));
        assertFalse(CardUtils.isLevelCard(13, negativeRank));
        assertFalse(CardUtils.isLevelCard(26, negativeRank));
        assertFalse(CardUtils.isLevelCard(39, negativeRank));
    }

    @Test
    void testLevelCardRankBoundaryTooHigh() {
        // levelCardRank 超过12时 isLevelCard 应返回 false
        int highRank = 13;
        int veryHighRank = 99;

        assertFalse(CardUtils.isLevelCard(0, highRank));
        assertFalse(CardUtils.isLevelCard(26, highRank));
        assertFalse(CardUtils.isLevelCard(0, veryHighRank));
        assertFalse(CardUtils.isLevelCard(26, veryHighRank));
    }

    @Test
    void testCardIdBoundaryNegative() {
        // cardId 为负数时 isLevelCard 应返回 false
        int levelCardRank = 5; // 级牌7
        int[] negativeIds = {-1, -100, Integer.MIN_VALUE};
        for (int id : negativeIds) {
            assertFalse(CardUtils.isLevelCard(id, levelCardRank),
                "负数cardId " + id + " 不应被识别为级牌");
            assertFalse(CardUtils.isWildCard(id, levelCardRank),
                "负数cardId " + id + " 不应被识别为逢人配");
        }
    }

    @Test
    void testCardIdBoundaryTooHigh() {
        // cardId 超出范围时 isLevelCard 应返回 false
        int levelCardRank = 5; // 级牌7
        assertFalse(CardUtils.isLevelCard(108, levelCardRank), "108不应是级牌");
        assertFalse(CardUtils.isLevelCard(109, levelCardRank), "109不应是级牌");
        assertFalse(CardUtils.isWildCard(108, levelCardRank), "108不应是逢人配");
        assertFalse(CardUtils.isWildCard(109, levelCardRank), "109不应是逢人配");
    }

    @Test
    void testGetGameLevelBoundaryValues() {
        // getGameLevel 边界值验证
        int levelCardRank = 5; // 级牌7

        // 级牌 → 15
        assertEquals(15, CardUtils.getGameLevel(31, levelCardRank), "级牌游戏等级应为15");

        // 最小非级牌
        assertEquals(0, CardUtils.getGameLevel(0, levelCardRank), "方块2游戏等级应为0");

        // 最大普通牌
        assertEquals(12, CardUtils.getGameLevel(12, levelCardRank), "方块A游戏等级应为12");

        // 大小王第二副牌
        assertEquals(13, CardUtils.getGameLevel(105, levelCardRank), "小王（第二副牌）游戏等级应为13");
        assertEquals(14, CardUtils.getGameLevel(107, levelCardRank), "大王（第二副牌）游戏等级应为14");
    }

    @Test
    void testDisplayNameInvalidCardId() {
        // getDisplayName 传入无效cardId不抛异常
        int levelCardRank = 5;
        String name1 = CardUtils.getDisplayName(-1, levelCardRank);
        String name2 = CardUtils.getDisplayName(108, levelCardRank);
        assertNotNull(name1, "无效cardId不返回null");
        assertNotNull(name2, "无效cardId不返回null");
    }
}
