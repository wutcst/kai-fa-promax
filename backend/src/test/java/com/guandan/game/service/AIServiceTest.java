package com.guandan.game.service;

import com.guandan.game.model.GameRoom;
import com.guandan.service.GameReferee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AIService JUnit5 单元测试套件
 *
 * <h3>测试范围</h3>
 * <ul>
 *   <li>playCards - 空手牌、自由出牌、跟牌、炸弹压制、无牌可出</li>
 *   <li>playFirstCard - 单牌优先策略、对子/三张/顺子降级</li>
 *   <li>findBomb - 有炸弹/无炸弹场景</li>
 *   <li>isAIPlayer - AI/真人玩家识别</li>
 *   <li>自学习权重引擎 - recordOutcome、getWinRate、getRecommendedCardTypes</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AIServiceTest {

    @Mock
    private GameReferee gameReferee;

    @InjectMocks
    private AIService aiService;

    private GameRoom gameRoom;
    private static final String AI_PLAYER_ID = "ai_player_0";
    private static final String HUMAN_PLAYER_ID = "1001";
    private static final int LEVEL_CARD_RANK = 0; // 级牌2

    @BeforeEach
    void setUp() {
        gameRoom = new GameRoom("test_room_001");
        gameRoom.addPlayer(AI_PLAYER_ID);
        gameRoom.addPlayer("1002");
        gameRoom.addPlayer("1003");
        gameRoom.addPlayer("1004");
        gameRoom.setLevelCardRank(LEVEL_CARD_RANK);
        gameRoom.setStatus(GameRoom.GameStatus.PLAYING);
    }

    @Nested
    @DisplayName("playCards - 主出牌方法")
    class PlayCardsTests {

        @Test
        @DisplayName("空手牌返回null")
        void emptyHandReturnsNull() {
            // Arrange: 给AI玩家空手牌
            gameRoom.setHandCards(AI_PLAYER_ID, new ArrayList<>());

            // Act
            List<Integer> result = aiService.playCards(gameRoom, AI_PLAYER_ID, LEVEL_CARD_RANK);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("自由出牌场景 - 上一手为null时调用playFirstCard")
        void freePlayWhenLastCardTypeNull() {
            // Arrange: 给AI手牌
            List<Integer> hand = Arrays.asList(0, 1, 5, 10, 26, 39, 52, 78, 104);
            gameRoom.setHandCards(AI_PLAYER_ID, new ArrayList<>(hand));
            gameRoom.setLastCardType(null);

            // Act
            List<Integer> result = aiService.playCards(gameRoom, AI_PLAYER_ID, LEVEL_CARD_RANK);

            // Assert: 应该出牌（非空）
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("跟牌场景 - 成功找到更大的牌型")
        void followPlaySuccess() {
            // Arrange: 设置上一手牌和对家信息
            List<Integer> hand = Arrays.asList(5, 18, 31, 44, 57, 70, 83, 96); // 全是7点
            gameRoom.setHandCards(AI_PLAYER_ID, new ArrayList<>(hand));
            gameRoom.setLastCardType("单张");
            gameRoom.setLastCardValue(3); // 上一手出了点数3的牌
            gameRoom.setLastPlayerId("1003");
            List<Integer> lastHand = Collections.singletonList(1); // 方块3
            gameRoom.setLastHandCards(lastHand);

            when(gameReferee.isValidHand(anyList(), anyInt())).thenReturn(true);
            when(gameReferee.canBeat(eq(lastHand), anyList(), anyInt())).thenReturn(true);

            // Act
            List<Integer> result = aiService.playCards(gameRoom, AI_PLAYER_ID, LEVEL_CARD_RANK);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            // 应该出了比3大的牌
            assertTrue(result.stream().anyMatch(cardId -> cardId.equals(5) || cardId.equals(18) ||
                    cardId.equals(31) || cardId.equals(44)));
        }

        @Test
        @DisplayName("跟牌时牌型不合法 - 尝试炸弹")
        void followPlayInvalidHandThenBomb() {
            // Arrange
            List<Integer> hand = Arrays.asList(0, 1, 13, 14, 26, 27, 39, 40); // 4个2+其他
            gameRoom.setHandCards(AI_PLAYER_ID, new ArrayList<>(hand));
            gameRoom.setLastCardType("顺子");
            gameRoom.setLastCardValue(5);
            gameRoom.setLastPlayerId("1003");
            List<Integer> lastHand = Arrays.asList(5, 6, 7, 8, 9);
            gameRoom.setLastHandCards(lastHand);

            // isValidHand 返回 false → 进入炸弹尝试
            when(gameReferee.isValidHand(anyList(), anyInt())).thenReturn(false);
            when(gameReferee.canBeat(eq(lastHand), anyList(), anyInt())).thenReturn(true);

            // Act
            List<Integer> result = aiService.playCards(gameRoom, AI_PLAYER_ID, LEVEL_CARD_RANK);

            // Assert: 应该出炸弹（4张2）
            assertNotNull(result);
            assertEquals(4, result.size());
        }

        @Test
        @DisplayName("跟牌时无法管住上一手 - 尝试炸弹")
        void followPlayCannotBeatThenBomb() {
            // Arrange
            List<Integer> hand = Arrays.asList(0, 1, 13, 14, 26, 27, 39, 40); // 4个2
            gameRoom.setHandCards(AI_PLAYER_ID, new ArrayList<>(hand));
            gameRoom.setLastCardType("三张");
            gameRoom.setLastCardValue(12); // A级别，很大
            gameRoom.setLastPlayerId("1003");
            List<Integer> lastHand = Arrays.asList(36, 49, 62); // 3张Q
            gameRoom.setLastHandCards(lastHand);

            when(gameReferee.isValidHand(anyList(), anyInt())).thenReturn(true);
            // canBeat 第一轮返回false → 触发炸弹尝试
            when(gameReferee.canBeat(eq(lastHand), anyList(), anyInt())).thenReturn(false)
                    .thenReturn(true); // 第二次是炸弹canBeat

            // Act
            List<Integer> result = aiService.playCards(gameRoom, AI_PLAYER_ID, LEVEL_CARD_RANK);

            // Assert: 应该出炸弹
            assertNotNull(result);
            assertEquals(4, result.size());
        }

        @Test
        @DisplayName("无牌可出时返回null")
        void noPlayableCardsReturnsNull() {
            // Arrange: 很小的牌面对很大的上一手
            List<Integer> hand = Arrays.asList(0, 1, 2, 3, 4);
            gameRoom.setHandCards(AI_PLAYER_ID, new ArrayList<>(hand));
            gameRoom.setLastCardType("单张");
            gameRoom.setLastCardValue(12); // A
            gameRoom.setLastPlayerId("1003");
            List<Integer> lastHand = Collections.singletonList(12); // 方块A
            gameRoom.setLastHandCards(lastHand);

            when(gameReferee.isValidHand(anyList(), anyInt())).thenReturn(true);
            when(gameReferee.canBeat(eq(lastHand), anyList(), anyInt())).thenReturn(false);

            // Act
            List<Integer> result = aiService.playCards(gameRoom, AI_PLAYER_ID, LEVEL_CARD_RANK);

            // Assert
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("isAIPlayer - AI玩家识别")
    class IsAIPlayerTests {

        @Test
        @DisplayName("ai_前缀返回true")
        void aiPrefixReturnsTrue() {
            assertTrue(aiService.isAIPlayer("ai_123"));
            assertTrue(aiService.isAIPlayer("ai_player_0"));
            assertTrue(aiService.isAIPlayer("ai_player_99"));
        }

        @Test
        @DisplayName("非AI前缀返回false")
        void nonAiPrefixReturnsFalse() {
            assertFalse(aiService.isAIPlayer("player_1"));
            assertFalse(aiService.isAIPlayer("1001"));
            assertFalse(aiService.isAIPlayer("test_user"));
            assertFalse(aiService.isAIPlayer(""));
        }

        @Test
        @DisplayName("null参数返回false")
        void nullReturnsFalse() {
            assertFalse(aiService.isAIPlayer(null));
        }
    }

    @Nested
    @DisplayName("findBomb - 炸弹检测")
    class FindBombTests {

        @Test
        @DisplayName("手牌中有4张同点数 - 返回炸弹")
        void handWithBombReturnsBomb() {
            List<Integer> hand = Arrays.asList(0, 13, 26, 39); // 4张2
            List<Integer> bomb = aiService.findBomb(hand, LEVEL_CARD_RANK);
            assertNotNull(bomb);
            assertEquals(4, bomb.size());
        }

        @Test
        @DisplayName("手牌中无炸弹 - 返回null")
        void handWithoutBombReturnsNull() {
            List<Integer> hand = Arrays.asList(0, 1, 2, 3, 4, 5);
            List<Integer> bomb = aiService.findBomb(hand, LEVEL_CARD_RANK);
            assertNull(bomb);
        }

        @Test
        @DisplayName("5张同点数也返回4张炸弹")
        void fiveSameRankReturnsBomb() {
            // 2点有4张（两副牌共8张），这里用5个数字验证
            List<Integer> hand = Arrays.asList(0, 13, 26, 39, 52, 65, 78);
            List<Integer> bomb = aiService.findBomb(hand, LEVEL_CARD_RANK);
            assertNotNull(bomb);
            assertEquals(4, bomb.size());
        }

        @Test
        @DisplayName("空手牌返回null")
        void emptyHandReturnsNull() {
            assertNull(aiService.findBomb(new ArrayList<>(), LEVEL_CARD_RANK));
        }
    }

    @Nested
    @DisplayName("playFirstCard - 自由出牌策略")
    class PlayFirstCardTests {

        @Test
        @DisplayName("有单牌时优先出单牌")
        void singleCardPreferred() {
            List<Integer> hand = Arrays.asList(0, 1, 2, 3, 4, 5);
            List<Integer> result = aiService.playFirstCard(hand, LEVEL_CARD_RANK);
            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("无单牌时出对子")
        void pairWhenNoSingle() {
            // 全是对子（不可能真的实现无单牌但只有对子，这里用全是相同点数的牌构造）
            List<Integer> hand = Arrays.asList(0, 13, 1, 14, 2, 15);
            List<Integer> result = aiService.playFirstCard(hand, LEVEL_CARD_RANK);
            assertNotNull(result);
        }

        @Test
        @DisplayName("无牌可出时返回null")
        void emptyHandReturnsNull() {
            assertNull(aiService.playFirstCard(new ArrayList<>(), LEVEL_CARD_RANK));
        }
    }

    @Nested
    @DisplayName("generateAIPlayerId - AI玩家ID生成")
    class GenerateAIPlayerIdTests {

        @Test
        @DisplayName("生成格式正确")
        void correctFormat() {
            assertEquals("ai_player_0", aiService.generateAIPlayerId(0));
            assertEquals("ai_player_1", aiService.generateAIPlayerId(1));
            assertEquals("ai_player_99", aiService.generateAIPlayerId(99));
        }
    }

    @Nested
    @DisplayName("自学习权重引擎 - LearningWeightEngine")
    class LearningWeightEngineTests {

        private AIService.LearningWeightEngine engine;

        @BeforeEach
        void setUp() {
            engine = aiService.getLearningEngine();
            engine.reset();
        }

        @Test
        @DisplayName("初始胜率为0.5")
        void initialWinRateIsDefault() {
            assertEquals(0.5, engine.getOverallWinRate());
        }

        @Test
        @DisplayName("记录对局胜率后正确更新")
        void recordGameResultUpdatesWinRate() {
            engine.recordGameResult(true);
            engine.recordGameResult(true);
            engine.recordGameResult(false);
            assertEquals(2.0 / 3.0, engine.getOverallWinRate(), 0.001);
        }

        @Test
        @DisplayName("记录出牌结果后统计数据正确")
        void recordPlayOutcomeUpdatesStats() {
            engine.recordPlayOutcome("单张", true);
            engine.recordPlayOutcome("单张", true);
            engine.recordPlayOutcome("单张", false);

            Map<String, Map<String, Object>> summary = engine.getStatisticsSummary();
            assertTrue(summary.containsKey("单张"));
            Map<String, Object> stats = summary.get("单张");
            assertEquals(3, stats.get("playCount"));
            assertEquals(2.0 / 3.0, (Double) stats.get("winRate"), 0.001);
        }

        @Test
        @DisplayName("未知牌型跳过统计")
        void unknownCardTypeSkipped() {
            engine.recordPlayOutcome("未知牌型", true);
            Map<String, Map<String, Object>> summary = engine.getStatisticsSummary();
            // 所有牌型playCount都应为0
            for (Map<String, Object> stat : summary.values()) {
                assertEquals(0, stat.get("playCount"));
            }
        }

        @Test
        @DisplayName("无统计记录时getRecommendedCardTypes返回空")
        void noDataReturnsEmptyRecommendations() {
            List<AIService.CardPlayType> recommended = engine.getRecommendedCardTypes();
            assertTrue(recommended.isEmpty());
        }

        @Test
        @DisplayName("有数据后按权重降序推荐")
        void dataReturnsWeightSortedRecommendations() {
            engine.recordPlayOutcome("单张", true);
            engine.recordPlayOutcome("对子", false);
            engine.recordPlayOutcome("单张", true);

            List<AIService.CardPlayType> recommended = engine.getRecommendedCardTypes();
            assertFalse(recommended.isEmpty());
            // 单张胜率高，应在前面
            int singleIdx = recommended.indexOf(AIService.CardPlayType.SINGLE);
            int pairIdx = recommended.indexOf(AIService.CardPlayType.PAIR);
            assertTrue(singleIdx < pairIdx);
        }

        @Test
        @DisplayName("无使用记录的牌型getWeightAdjustment返回1.0")
        void noDataReturnsDefaultAdjustment() {
            double adj = engine.getWeightAdjustment("单张");
            assertEquals(1.0, adj, 0.001);
        }

        @Test
        @DisplayName("重置后所有统计归零")
        void resetClearsAllStats() {
            engine.recordGameResult(true);
            engine.recordPlayOutcome("单张", true);
            engine.reset();

            assertEquals(0.5, engine.getOverallWinRate());
            assertTrue(engine.getRecommendedCardTypes().isEmpty());
            // 所有牌型重置
            Map<String, Map<String, Object>> summary = engine.getStatisticsSummary();
            for (Map.Entry<String, Map<String, Object>> entry : summary.entrySet()) {
                assertEquals(0, entry.getValue().get("playCount"));
                assertEquals(0.5, (Double) entry.getValue().get("winRate"), 0.001);
            }
        }

        @Test
        @DisplayName("设置学习率边界检查")
        void learningRateBoundaryCheck() {
            engine.setLearningRate(-0.1);
            assertNotEquals(-0.1, engine.getLearningRate());
            engine.setLearningRate(1.5);
            assertNotEquals(1.5, engine.getLearningRate());
            engine.setLearningRate(0.5);
            assertEquals(0.5, engine.getLearningRate());
        }
    }
}
