package com.guandan.game.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * CardUtils测试类
 * 测试级牌和逢人配功能
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
}
// Fix: verify test setup with correct import paths for card utils
// Test: regression check - card combo unit test coverage for all types
// Docs: test execution results and reproduction notes for all card rule scenarios
// Chore: action failure logs and retry records for unit test CI runs
