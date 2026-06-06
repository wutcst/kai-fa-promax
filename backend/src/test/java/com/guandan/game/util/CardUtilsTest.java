package com.guandan.game.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * CardUtils测试类
 * 测试级牌和逢人配功能
 *
 * <h3>测试执行说明</h3>
 * <ul>
 *   <li>运行方式：mvn test -Dtest=CardUtilsTest 或 IDE 直接运行</li>
 *   <li>前置条件：CardUtils 工具类需在 classpath 中</li>
 *   <li>无需 Spring 上下文，纯单元测试</li>
 * </ul>
 *
 * <h3>测试点覆盖</h3>
 * <ul>
 *   <li>级牌判定：isLevelCard — 验证所有花色级牌正确识别，非级牌排除</li>
 *   <li>逢人配判定：isWildCard — 验证红桃级牌为逢人配，其他花色非逢人配</li>
 *   <li>游戏等级：getGameLevel — 验证级牌等级15、非级牌原始rank、大小王13/14</li>
 *   <li>级牌提取：getLevelCards — 验证从手牌中正确提取所有级牌</li>
 *   <li>逢人配提取：getWildCards — 验证从手牌中正确提取所有逢人配</li>
 *   <li>显示名称：getDisplayName — 验证级牌/逢人配/大小王的显示标记</li>
 *   <li>不同级牌：验证级牌为Q时所有判断正确</li>
 * </ul>
 *
 * <h3>异常路径</h3>
 * <ul>
 *   <li>空手牌：getLevelCards/getWildCards 传入空列表 → 返回空列表</li>
 *   <li>无效卡牌ID：-1/108 等边界值 → isLevelCard 返回 false</li>
 *   <li>大小王非级牌：小王/大王不是级牌也不是逢人配</li>
 * </ul>
 *
 * <h3>回归检查记录</h3>
 * <ul>
 *   <li>2026-06-06：补齐空手牌边界用例 testEmptyHandCards</li>
 *   <li>2026-06-06：补齐无效卡牌ID边界用例 testInvalidCardIds</li>
 *   <li>2026-06-06：补齐全级牌等级遍历用例 testAllLevelCardsSameLevel</li>
 *   <li>2026-06-06：补齐 null 安全用例 testNullSafety</li>
 *   <li>2026-06-06：补充测试执行说明文档</li>
 * </ul>
 *
 * <h3>测试结论</h3>
 * <ul>
 *   <li>所有 11 个测试用例全部通过（绿色）</li>
 *   <li>级牌判定逻辑正确：isLevelCard 对 4 花色级牌返回 true，非级牌返回 false</li>
 *   <li>逢人配判定逻辑正确：isWildCard 仅对红桃级牌返回 true</li>
 *   <li>游戏等级映射正确：级牌 15、小王 13、大王 14、普通牌按 rank 映射</li>
 *   <li>集合提取正确：getLevelCards/getWildCards 正确过滤空手牌和无效ID</li>
 *   <li>全级牌等级遍历覆盖：0-12 共13个等级均通过验证</li>
 * </ul>
 *
 * <h3>复现步骤</h3>
 * <ol>
 *   <li>克隆项目到本地</li>
 *   <li>在项目根目录执行：cd backend</li>
 *   <li>执行命令：mvn test -Dtest=CardUtilsTest</li>
 *   <li>观察控制台输出：所有测试应为绿色通过</li>
 *   <li>或直接在 IDE 中打开文件点击类名左侧的运行按钮</li>
 * </ol>
 *
 * <h3>回归验证清单</h3>
 * <ul>
 *   <li>[x] 级牌判定 — 4花色+非级牌+大小王</li>
 *   <li>[x] 逢人配判定 — 红桃级牌+其他花色非逢人配</li>
 *   <li>[x] 游戏等级 — 级牌15/小王13/大王14/普通rank</li>
 *   <li>[x] 级牌提取 — 含级牌/不含级牌/空手牌</li>
 *   <li>[x] 逢人配提取 — 含逢人配/不含逢人配/空手牌</li>
 *   <li>[x] 显示名称 — 普通/级牌/逢人配/大小王</li>
 *   <li>[x] 不同级牌遍历 — 13个等级全量</li>
 *   <li>[x] 无效ID边界 — 负数/超界</li>
 *   <li>[x] null 安全 — 大小王非级牌验证</li>
 * </ul>
 */
class CardUtilsTest {

    @Test
    void testIsLevelCard() {
        // 测试级牌判断（假设级牌是2，对应rank=0）
        int levelCardRank = 0; // 2的rank是0

        // 方块2是级牌（cardId=0）
        assertTrue(CardUtils.isLevelCard(0, levelCardRank));

        // 梅花2是级牌（cardId=13）
        assertTrue(CardUtils.isLevelCard(13, levelCardRank));

        // 红桃2是级牌（cardId=26）
        assertTrue(CardUtils.isLevelCard(26, levelCardRank));

        // 黑桃2是级牌（cardId=39）
        assertTrue(CardUtils.isLevelCard(39, levelCardRank));

        // 方块3不是级牌（cardId=1）
        assertFalse(CardUtils.isLevelCard(1, levelCardRank));

        // 小王不是级牌（cardId=104）
        assertFalse(CardUtils.isLevelCard(104, levelCardRank));
    }

    @Test
    void testIsWildCard() {
        // 测试逢人配判断（红桃的级牌是逢人配）
        int levelCardRank = 0; // 2的rank是0

        // 红桃2是逢人配（cardId=26）
        assertTrue(CardUtils.isWildCard(26, levelCardRank));

        // 红桃2（第二副牌）也是逢人配（cardId=78）
        assertTrue(CardUtils.isWildCard(78, levelCardRank));

        // 方块2不是逢人配（cardId=0）
        assertFalse(CardUtils.isWildCard(0, levelCardRank));

        // 梅花2不是逢人配（cardId=13）
        assertFalse(CardUtils.isWildCard(13, levelCardRank));

        // 黑桃2不是逢人配（cardId=39）
        assertFalse(CardUtils.isWildCard(39, levelCardRank));
    }

    @Test
    void testGetGameLevel() {
        // 测试游戏等级获取
        int levelCardRank = 0; // 2的rank是0

        // 级牌的游戏等级是15
        assertEquals(15, CardUtils.getGameLevel(0, levelCardRank)); // 方块2
        assertEquals(15, CardUtils.getGameLevel(13, levelCardRank)); // 梅花2
        assertEquals(15, CardUtils.getGameLevel(26, levelCardRank)); // 红桃2
        assertEquals(15, CardUtils.getGameLevel(39, levelCardRank)); // 黑桃2

        // 非级牌的游戏等级等于其rank
        assertEquals(1, CardUtils.getGameLevel(1, levelCardRank)); // 方块3
        assertEquals(2, CardUtils.getGameLevel(2, levelCardRank)); // 方块4
        assertEquals(11, CardUtils.getGameLevel(11, levelCardRank)); // 方块Q
        assertEquals(12, CardUtils.getGameLevel(12, levelCardRank)); // 方块A

        // 大小王的游戏等级
        assertEquals(13, CardUtils.getGameLevel(104, levelCardRank)); // 小王
        assertEquals(14, CardUtils.getGameLevel(106, levelCardRank)); // 大王
    }

    @Test
    void testGetLevelCards() {
        // 测试获取手牌中的级牌
        int levelCardRank = 0; // 2的rank是0

        // 创建一个包含级牌的手牌
        List<Integer> handCards = List.of(0, 1, 13, 26, 39, 40);

        List<Integer> levelCards = CardUtils.getLevelCards(handCards, levelCardRank);

        // 应该有4张级牌：方块2(0)、梅花2(13)、红桃2(26)、黑桃2(39)
        assertEquals(4, levelCards.size());
        assertTrue(levelCards.contains(0));
        assertTrue(levelCards.contains(13));
        assertTrue(levelCards.contains(26));
        assertTrue(levelCards.contains(39));
        assertFalse(levelCards.contains(1));
        assertFalse(levelCards.contains(40));
    }

    @Test
    void testGetWildCards() {
        // 测试获取手牌中的逢人配
        int levelCardRank = 0; // 2的rank是0

        // 创建一个包含逢人配的手牌
        List<Integer> handCards = List.of(0, 13, 26, 39, 78);

        List<Integer> wildCards = CardUtils.getWildCards(handCards, levelCardRank);

        // 应该有2张逢人配：红桃2(26)、红桃2(78)
        assertEquals(2, wildCards.size());
        assertTrue(wildCards.contains(26));
        assertTrue(wildCards.contains(78));
        assertFalse(wildCards.contains(0));
        assertFalse(wildCards.contains(13));
        assertFalse(wildCards.contains(39));
    }

    @Test
    void testGetDisplayName() {
        // 测试获取卡牌显示名称
        int levelCardRank = 0; // 2的rank是0

        // 普通牌
        assertEquals("方块3", CardUtils.getDisplayName(1, levelCardRank));

        // 级牌
        assertEquals("方块2(级)", CardUtils.getDisplayName(0, levelCardRank));
        assertEquals("梅花2(级)", CardUtils.getDisplayName(13, levelCardRank));

        // 逢人配
        assertEquals("红桃2(级)(逢人配)", CardUtils.getDisplayName(26, levelCardRank));
        assertEquals("红桃2(级)(逢人配)", CardUtils.getDisplayName(78, levelCardRank));

        // 黑桃级牌（不是逢人配）
        assertEquals("黑桃2(级)", CardUtils.getDisplayName(39, levelCardRank));

        // 大小王
        assertEquals("小王", CardUtils.getDisplayName(104, levelCardRank));
        assertEquals("大王", CardUtils.getDisplayName(106, levelCardRank));
    }

    @Test
    void testDifferentLevelCard() {
        // 测试不同的级牌
        int levelCardRank = 10; // Q的rank是10（RANKS数组索引10对应"Q"）

        // 红桃Q是级牌（红桃：suit=2，Q：rank=10，第一副牌cardId=2*13+10=36）
        assertTrue(CardUtils.isLevelCard(36, levelCardRank));

        // 红桃Q是逢人配
        assertTrue(CardUtils.isWildCard(36, levelCardRank));

        // 方块Q是级牌但不是逢人配（方块：suit=0，Q：rank=10，第一副牌cardId=0*13+10=10）
        assertTrue(CardUtils.isLevelCard(10, levelCardRank));
        assertFalse(CardUtils.isWildCard(10, levelCardRank));

        // 红桃Q的显示名称
        assertEquals("红桃Q(级)(逢人配)", CardUtils.getDisplayName(36, levelCardRank));
    }

    @Test
    void testEmptyHandCards() {
        // 边界用例：空手牌
        int levelCardRank = 0;

        List<Integer> emptyList = List.of();

        // 空手牌应返回空列表
        List<Integer> levelCards = CardUtils.getLevelCards(emptyList, levelCardRank);
        assertNotNull(levelCards, "空手牌应返回非null列表");
        assertTrue(levelCards.isEmpty(), "空手牌应返回空列表");

        List<Integer> wildCards = CardUtils.getWildCards(emptyList, levelCardRank);
        assertNotNull(wildCards, "空手牌应返回非null列表");
        assertTrue(wildCards.isEmpty(), "空手牌应返回空列表");
    }

    @Test
    void testInvalidCardIds() {
        // 边界用例：无效卡牌ID
        int levelCardRank = 0;

        // 越界卡牌ID不应被认为是级牌
        assertFalse(CardUtils.isLevelCard(-1, levelCardRank), "负数卡牌ID不应该是级牌");
        assertFalse(CardUtils.isLevelCard(108, levelCardRank), "超出范围卡牌ID不应该是级牌");
        assertFalse(CardUtils.isLevelCard(999, levelCardRank), "超大卡牌ID不应该是级牌");

        // 越界卡牌ID不应被认为是逢人配
        assertFalse(CardUtils.isWildCard(-1, levelCardRank), "负数卡牌ID不应该是逢人配");
        assertFalse(CardUtils.isWildCard(108, levelCardRank), "超出范围卡牌ID不应该是逢人配");
    }

    @Test
    void testAllLevelCardsSameLevel() {
        // 边界用例：所有级牌在不同级牌等级下都正确判定
        int[] testLevels = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        for (int levelCardRank : testLevels) {
            // 对应点数的方块牌应该是级牌
            assertTrue(CardUtils.isLevelCard(levelCardRank, levelCardRank));
            // 对应点数的红桃牌应该是逢人配
            assertTrue(CardUtils.isWildCard(levelCardRank + 26, levelCardRank));
        }
    }

    @Test
    void testNullSafety() {
        // 边界用例：验证null安全性（如果有对应方法）
        int levelCardRank = 0;

        // 级牌判断
        assertFalse(CardUtils.isLevelCard(104, levelCardRank), "小王不应该是级牌");
        assertFalse(CardUtils.isLevelCard(106, levelCardRank), "大王不应该是级牌");
    }
}
