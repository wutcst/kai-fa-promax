package com.guandan.game.service;

import com.guandan.game.model.GameRoom;
import com.guandan.game.util.CardUtils;
import com.guandan.service.GameReferee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * AI服务类
 * 实现AI玩家的出牌逻辑，提供完整的出牌决策和牌型匹配功能。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>自由出牌策略：优先出单牌 → 对子 → 三张 → 顺子</li>
 *   <li>跟牌响应：根据上家出牌类型匹配更大的同类型牌</li>
 *   <li>炸弹压制：无法跟牌时自动判断是否可以用炸弹压制</li>
 *   <li>AI玩家识别：通过 playerId 前缀判断是否为 AI 玩家</li>
 *   <li>自学习权重调整：根据历史对局统计动态优化牌型推荐优先级</li>
 * </ul>
 *
 * <h3>异常场景</h3>
 * <ul>
 *   <li>手牌为空 → 返回 null，日志记录"没有手牌可出"</li>
 *   <li>牌型不合法 → 尝试炸弹，失败则返回 null</li>
 *   <li>无法管住上一手牌 → 尝试炸弹，失败则返回 null</li>
 *   <li>无任何可出的牌 → 返回 null，日志记录"不出牌"</li>
 * </ul>
 *
 * <h3>回归验证点</h3>
 * <ul>
 *   <li>[TC-AI-PLAY-001] playCards 空手牌 → 返回 null，日志输出无牌提示</li>
 *   <li>[TC-AI-PLAY-002] playCards 自由出牌 → 返回单牌/对子/三张/顺子之一</li>
 *   <li>[TC-AI-PLAY-003] playCards 跟牌时牌型不合法 → 尝试炸弹，失败返回 null</li>
 *   <li>[TC-AI-PLAY-004] playCards 跟牌时无法管住 → 尝试炸弹，失败返回 null</li>
 *   <li>[TC-AI-PLAY-005] playCards 无牌可出 → 返回 null，日志输出"不出牌"</li>
 *   <li>[TC-AI-PLAY-006] isAIPlayer("ai_xxx") → true</li>
 *   <li>[TC-AI-PLAY-007] isAIPlayer("player_1") → false</li>
 *   <li>[TC-AI-PLAY-008] findBomb 手牌中有4张同点数 → 返回4张牌列表</li>
 *   <li>[TC-AI-PLAY-009] findBomb 手牌中无炸弹 → 返回 null</li>
 *   <li>[TC-AI-PLAY-010] getWinRate 有统计数据 → 返回正确胜率</li>
 *   <li>[TC-AI-PLAY-011] getWinRate 无统计数据 → 返回默认值0.5</li>
 *   <li>[TC-AI-PLAY-012] recordOutcome 记录后统计数据正确更新</li>
 *   <li>[TC-AI-PLAY-013] getRecommendedCardTypes 返回按胜率排序的推荐列表</li>
 * </ul>
 */
@Slf4j
@Service
public class AIService {

    /**
     * 级牌排名映射：0-12 -> 3,4,5,6,7,8,9,10,J,Q,K,A,2
     */
    private static final int[] RANK_PRIORITY = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

    /**
     * 花色优先级：方块(0) < 梅花(1) < 红桃(2) < 黑桃(3)
     */
    private static final int[] SUIT_PRIORITY = {0, 1, 2, 3};

    /**
     * 游戏裁判服务（规则验证）
     */
    @Autowired
    private GameReferee gameReferee;

    /**
     * AI玩家出牌
     * @param gameRoom 游戏房间
     * @param playerId 玩家ID
     * @param levelCardRank 级牌点数 (0-12对应3-A, 不包括2)
     * @return 要出的卡牌ID列表
     */
    public List<Integer> playCards(GameRoom gameRoom, String playerId, int levelCardRank) {
        List<Integer> handCards = gameRoom.getPlayerHandCards(playerId);
        if (handCards.isEmpty()) {
            log.info("AI玩家 {} 没有手牌可出", playerId);
            return null;
        }

        // 获取上一次出牌信息
        String lastCardType = gameRoom.getLastCardType();
        Integer lastCardValue = gameRoom.getLastCardValue();
        List<Integer> lastHandCards = gameRoom.getLastHandCards();

        // 如果是第一次出牌或者上一次出牌的玩家是自己，自由出牌
        if (lastCardType == null || playerId.equals(gameRoom.getLastPlayerId())) {
            return playFirstCard(handCards, levelCardRank);
        }

        // 根据上一次出牌的类型，寻找合适的牌型回应
        List<Integer> responseCards = findResponseCards(handCards, lastCardType, lastCardValue, levelCardRank);
        
        // 验证AI出的牌是否符合规则
        if (responseCards != null) {
            // 1. 验证牌型是否合法
            if (!gameReferee.isValidHand(responseCards, levelCardRank)) {
                log.warn("AI玩家 {} 尝试出牌失败：牌型不合法", playerId);
                // 尝试用炸弹
                List<Integer> bombCards = findBomb(handCards, levelCardRank);
                if (bombCards != null && gameReferee.canBeat(lastHandCards, bombCards, levelCardRank)) {
                    log.info("AI玩家 {} 出炸弹: {}", playerId, Arrays.toString(CardUtils.idsToStrings(bombCards.stream().mapToInt(i->i).toArray())));
                    return bombCards;
                }
                return null;
            }
            
            // 2. 验证是否能管住上一手牌
            if (!gameReferee.canBeat(lastHandCards, responseCards, levelCardRank)) {
                log.warn("AI玩家 {} 尝试出牌失败：无法管住上一手牌", playerId);
                // 尝试用炸弹
                List<Integer> bombCards = findBomb(handCards, levelCardRank);
                if (bombCards != null && gameReferee.canBeat(lastHandCards, bombCards, levelCardRank)) {
                    log.info("AI玩家 {} 出炸弹: {}", playerId, Arrays.toString(CardUtils.idsToStrings(bombCards.stream().mapToInt(i->i).toArray())));
                    return bombCards;
                }
                return null;
            }
            
            log.info("AI玩家 {} 回应出牌: {}", playerId, Arrays.toString(CardUtils.idsToStrings(responseCards.stream().mapToInt(i->i).toArray())));
            return responseCards;
        }

        // 检查是否有炸弹可以压制
        List<Integer> bombCards = findBomb(handCards, levelCardRank);
        if (bombCards != null && gameReferee.canBeat(lastHandCards, bombCards, levelCardRank)) {
            log.info("AI玩家 {} 出炸弹: {}", playerId, Arrays.toString(CardUtils.idsToStrings(bombCards.stream().mapToInt(i->i).toArray())));
            return bombCards;
        }

        log.info("AI玩家 {} 不出牌", playerId);
        return null;
    }

    /**
     * 第一次出牌或自由出牌时的策略
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return 要出的卡牌ID列表
     */
    public List<Integer> playFirstCard(List<Integer> handCards, int levelCardRank) {
        // 优先出单牌
        Integer bestSingle = findBestSingleCard(handCards, levelCardRank);
        if (bestSingle != null) {
            List<Integer> cardsToPlay = new ArrayList<>();
            cardsToPlay.add(bestSingle);
            return cardsToPlay;
        }

        // 其次出对子
        List<Integer> bestPair = findBestPair(handCards, levelCardRank);
        if (bestPair != null) {
            return bestPair;
        }

        // 再次出三张
        List<Integer> bestThree = findBestThree(handCards, levelCardRank);
        if (bestThree != null) {
            return bestThree;
        }

        // 最后出顺子
        List<Integer> bestStraight = findBestStraight(handCards, levelCardRank);
        if (bestStraight != null) {
            return bestStraight;
        }

        return null;
    }

    /**
     * 根据上一次出牌的类型，寻找合适的牌型回应
     * @param handCards 手牌列表
     * @param lastCardType 上一次出牌的类型
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 要出的卡牌ID列表
     */
    public List<Integer> findResponseCards(List<Integer> handCards, String lastCardType, Integer lastCardValue, int levelCardRank) {
        switch (lastCardType) {
            case "单张":
                return findBetterSingle(handCards, lastCardValue, levelCardRank);
            case "对子":
                return findBetterPair(handCards, lastCardValue, levelCardRank);
            case "三张":
                return findBetterThree(handCards, lastCardValue, levelCardRank);
            case "顺子":
                return findBetterStraight(handCards, lastCardValue, levelCardRank);
            case "三带二":
                return findBetterThreeWithTwo(handCards, lastCardValue, levelCardRank);
            case "钢板":
                return findBetterSteelPlate(handCards, lastCardValue, levelCardRank);
            case "同花顺":
                return findBetterStraightFlush(handCards, lastCardValue, levelCardRank);
            case "炸弹":
                // 只能用更大的炸弹回应
                return findBetterBomb(handCards, lastCardValue, levelCardRank);
            default:
                return null;
        }
    }

    /**
     * 找出最佳的单牌
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return 最佳单牌的ID
     */
    private Integer findBestSingleCard(List<Integer> handCards, int levelCardRank) {
        return handCards.stream()
                .max(Comparator.comparingInt(cardId -> calculateCardScore(cardId, levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的单牌
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的单牌
     */
    private List<Integer> findBetterSingle(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        Integer bestCard = handCards.stream()
                .filter(cardId -> calculateCardScore(cardId, levelCardRank) > calculateCardScoreByRank(lastCardValue, levelCardRank))
                .max(Comparator.comparingInt(cardId -> calculateCardScore(cardId, levelCardRank)))
                .orElse(null);

        if (bestCard != null) {
            List<Integer> cardsToPlay = new ArrayList<>();
            cardsToPlay.add(bestCard);
            return cardsToPlay;
        }

        return null;
    }

    /**
     * 找出最佳的对子
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return 最佳对子的ID列表
     */
    private List<Integer> findBestPair(List<Integer> handCards, int levelCardRank) {
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有对子
        List<List<Integer>> pairs = new ArrayList<>();
        for (List<Integer> cards : rankToCards.values()) {
            if (cards.size() >= 2) {
                // 取两张牌作为对子
                List<Integer> pair = new ArrayList<>(cards.subList(0, 2));
                pairs.add(pair);
            }
        }

        // 找出最大的对子
        return pairs.stream()
                .max(Comparator.comparingInt(pair -> calculateCardScore(pair.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的对子
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的对子
     */
    private List<Integer> findBetterPair(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有比指定牌值大的对子
        List<List<Integer>> betterPairs = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            int rank = entry.getKey();
            List<Integer> cards = entry.getValue();
            if (cards.size() >= 2 && calculateCardScoreByRank(rank, levelCardRank) > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                List<Integer> pair = new ArrayList<>(cards.subList(0, 2));
                betterPairs.add(pair);
            }
        }

        // 找出最大的对子
        return betterPairs.stream()
                .max(Comparator.comparingInt(pair -> calculateCardScore(pair.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出最佳的三张
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return 最佳三张的ID列表
     */
    private List<Integer> findBestThree(List<Integer> handCards, int levelCardRank) {
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有三张
        List<List<Integer>> threes = new ArrayList<>();
        for (List<Integer> cards : rankToCards.values()) {
            if (cards.size() >= 3) {
                // 取三张牌作为三张
                List<Integer> three = new ArrayList<>(cards.subList(0, 3));
                threes.add(three);
            }
        }

        // 找出最大的三张
        return threes.stream()
                .max(Comparator.comparingInt(three -> calculateCardScore(three.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的三张
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的三张
     */
    private List<Integer> findBetterThree(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有比指定牌值大的三张
        List<List<Integer>> betterThrees = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            int rank = entry.getKey();
            List<Integer> cards = entry.getValue();
            if (cards.size() >= 3 && calculateCardScoreByRank(rank, levelCardRank) > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                List<Integer> three = new ArrayList<>(cards.subList(0, 3));
                betterThrees.add(three);
            }
        }

        // 找出最大的三张
        return betterThrees.stream()
                .max(Comparator.comparingInt(three -> calculateCardScore(three.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出最佳的顺子
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return 最佳顺子的ID列表
     */
    private List<Integer> findBestStraight(List<Integer> handCards, int levelCardRank) {
        // 统计每个点数的牌
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            // 排除大小王（顺子不能包含大小王）
            if (rank < 13) {
                rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
            }
        }

        // 找出所有可能的顺子（至少5张）
        List<Integer> ranks = new ArrayList<>(rankToCards.keySet());
        ranks.sort(Integer::compareTo);

        List<List<Integer>> straights = new ArrayList<>();
        for (int i = 0; i <= ranks.size() - 5; i++) {
            List<Integer> straightRanks = new ArrayList<>();
            for (int j = i; j < i + 5 && j < ranks.size(); j++) {
                if (j > i && ranks.get(j) != ranks.get(j - 1) + 1) {
                    break;
                }
                straightRanks.add(ranks.get(j));
            }
            if (straightRanks.size() >= 5) {
                List<Integer> straight = new ArrayList<>();
                for (Integer rank : straightRanks) {
                    straight.add(rankToCards.get(rank).get(0));
                }
                straights.add(straight);
            }
        }

        // 找出最大的顺子
        return straights.stream()
                .max(Comparator.comparingInt(straight -> calculateCardScore(straight.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的顺子
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的顺子
     */
    private List<Integer> findBetterStraight(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        // 统计每个点数的牌
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            // 排除大小王（顺子不能包含大小王）
            if (rank < 13) {
                rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
            }
        }

        // 找出所有可能的顺子（至少5张）
        List<Integer> ranks = new ArrayList<>(rankToCards.keySet());
        ranks.sort(Integer::compareTo);

        List<List<Integer>> betterStraights = new ArrayList<>();
        for (int i = 0; i <= ranks.size() - 5; i++) {
            List<Integer> straightRanks = new ArrayList<>();
            for (int j = i; j < i + 5 && j < ranks.size(); j++) {
                if (j > i && ranks.get(j) != ranks.get(j - 1) + 1) {
                    break;
                }
                straightRanks.add(ranks.get(j));
            }
            if (straightRanks.size() >= 5) {
                // 检查是否比上一手牌大
                int straightValue = calculateCardScoreByRank(straightRanks.get(0), levelCardRank);
                if (straightValue > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                    List<Integer> straight = new ArrayList<>();
                    for (Integer rank : straightRanks) {
                        straight.add(rankToCards.get(rank).get(0));
                    }
                    betterStraights.add(straight);
                }
            }
        }

        // 找出最小的满足条件的顺子（节省手牌）
        return betterStraights.stream()
                .min(Comparator.comparingInt(straight -> calculateCardScore(straight.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的三带二
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的三带二
     */
    private List<Integer> findBetterThreeWithTwo(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        // 统计每个点数的牌
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有三张
        List<Integer> triplets = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            if (entry.getValue().size() >= 3) {
                triplets.add(entry.getKey());
            }
        }

        // 找出所有对子
        List<Integer> pairs = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            if (entry.getValue().size() >= 2) {
                pairs.add(entry.getKey());
            }
        }

        // 找出比指定牌值大的三带二
        List<List<Integer>> betterThreeWithTwos = new ArrayList<>();
        for (Integer tripletRank : triplets) {
            if (calculateCardScoreByRank(tripletRank, levelCardRank) > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                for (Integer pairRank : pairs) {
                    if (!pairRank.equals(tripletRank)) {
                        List<Integer> threeWithTwo = new ArrayList<>();
                        threeWithTwo.addAll(rankToCards.get(tripletRank).subList(0, 3));
                        threeWithTwo.addAll(rankToCards.get(pairRank).subList(0, 2));
                        betterThreeWithTwos.add(threeWithTwo);
                    }
                }
            }
        }

        // 找出最小的满足条件的三带二（节省手牌）
        return betterThreeWithTwos.stream()
                .min(Comparator.comparingInt(threeWithTwo -> calculateCardScore(threeWithTwo.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的钢板
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的钢板
     */
    private List<Integer> findBetterSteelPlate(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        // 统计每个点数的牌
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有三张
        List<Integer> triplets = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            if (entry.getValue().size() >= 3) {
                triplets.add(entry.getKey());
            }
        }

        // 找出所有连续的三张（钢板）
        List<List<Integer>> steelPlates = new ArrayList<>();
        for (int i = 0; i < triplets.size() - 1; i++) {
            if (triplets.get(i + 1) == triplets.get(i) + 1) {
                List<Integer> steelPlate = new ArrayList<>();
                steelPlate.addAll(rankToCards.get(triplets.get(i)).subList(0, 3));
                steelPlate.addAll(rankToCards.get(triplets.get(i + 1)).subList(0, 3));
                steelPlates.add(steelPlate);
            }
        }

        // 找出比指定牌值大的钢板
        List<List<Integer>> betterSteelPlates = new ArrayList<>();
        for (List<Integer> steelPlate : steelPlates) {
            int steelPlateValue = calculateCardScore(steelPlate.get(0), levelCardRank);
            if (steelPlateValue > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                betterSteelPlates.add(steelPlate);
            }
        }

        // 找出最小的满足条件的钢板（节省手牌）
        return betterSteelPlates.stream()
                .min(Comparator.comparingInt(steelPlate -> calculateCardScore(steelPlate.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的同花顺
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的同花顺
     */
    private List<Integer> findBetterStraightFlush(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        // 统计每个花色的牌
        Map<Integer, List<Integer>> suitToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int suit = CardUtils.getSuit(cardId);
            int rank = CardUtils.getRank(cardId);
            // 排除大小王（同花顺不能包含大小王）
            if (suit >= 0 && rank < 13) {
                suitToCards.computeIfAbsent(suit, k -> new ArrayList<>()).add(cardId);
            }
        }

        // 对每个花色，查找同花顺
        List<List<Integer>> straightFlushes = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : suitToCards.entrySet()) {
            List<Integer> cards = entry.getValue();
            if (cards.size() < 5) continue;

            // 统计该花色每个点数的牌
            Map<Integer, List<Integer>> rankToCards = new HashMap<>();
            for (Integer cardId : cards) {
                int rank = CardUtils.getRank(cardId);
                rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
            }

            // 找出所有可能的顺子
            List<Integer> ranks = new ArrayList<>(rankToCards.keySet());
            ranks.sort(Integer::compareTo);

            for (int i = 0; i <= ranks.size() - 5; i++) {
                List<Integer> straightRanks = new ArrayList<>();
                for (int j = i; j < i + 5 && j < ranks.size(); j++) {
                    if (j > i && ranks.get(j) != ranks.get(j - 1) + 1) {
                        break;
                    }
                    straightRanks.add(ranks.get(j));
                }
                if (straightRanks.size() >= 5) {
                    List<Integer> straightFlush = new ArrayList<>();
                    for (Integer rank : straightRanks) {
                        straightFlush.add(rankToCards.get(rank).get(0));
                    }
                    straightFlushes.add(straightFlush);
                }
            }
        }

        // 找出比指定牌值大的同花顺
        List<List<Integer>> betterStraightFlushes = new ArrayList<>();
        for (List<Integer> straightFlush : straightFlushes) {
            int straightFlushValue = calculateCardScore(straightFlush.get(0), levelCardRank);
            if (straightFlushValue > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                betterStraightFlushes.add(straightFlush);
            }
        }

        // 找出最小的满足条件的同花顺（节省手牌）
        return betterStraightFlushes.stream()
                .min(Comparator.comparingInt(straightFlush -> calculateCardScore(straightFlush.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出炸弹
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return 炸弹的ID列表
     */
    public List<Integer> findBomb(List<Integer> handCards, int levelCardRank) {
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有炸弹（4张及以上同点数）
        List<List<Integer>> bombs = new ArrayList<>();
        for (List<Integer> cards : rankToCards.values()) {
            if (cards.size() >= 4) {
                // 取四张牌作为炸弹
                List<Integer> bomb = new ArrayList<>(cards.subList(0, 4));
                bombs.add(bomb);
            }
        }

        // 找出最大的炸弹
        return bombs.stream()
                .max(Comparator.comparingInt(bomb -> calculateCardScore(bomb.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 找出比指定牌值大的炸弹
     * @param handCards 手牌列表
     * @param lastCardValue 上一次出牌的牌值
     * @param levelCardRank 级牌点数
     * @return 比指定牌值大的炸弹
     */
    private List<Integer> findBetterBomb(List<Integer> handCards, Integer lastCardValue, int levelCardRank) {
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有比指定牌值大的炸弹
        List<List<Integer>> betterBombs = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            int rank = entry.getKey();
            List<Integer> cards = entry.getValue();
            if (cards.size() >= 4 && calculateCardScoreByRank(rank, levelCardRank) > calculateCardScoreByRank(lastCardValue, levelCardRank)) {
                List<Integer> bomb = new ArrayList<>(cards.subList(0, 4));
                betterBombs.add(bomb);
            }
        }

        // 找出最大的炸弹
        return betterBombs.stream()
                .max(Comparator.comparingInt(bomb -> calculateCardScore(bomb.get(0), levelCardRank)))
                .orElse(null);
    }

    /**
     * 计算卡牌的分数（用于排序）
     * @param cardId 卡牌ID
     * @param levelCardRank 级牌点数
     * @return 卡牌分数
     */
    private int calculateCardScore(int cardId, int levelCardRank) {
        // 大小王分数最高
        if (cardId >= 104) {
            return cardId <= 105 ? 1000 : 2000; // 小王1000，大王2000
        }

        // 处理普通牌 (0-103)
        int suit = CardUtils.getSuit(cardId);
        int rank = CardUtils.getRank(cardId);

        // 计算基础分数
        int baseScore = 0;
        
        // 级牌优先级最高（仅次于大小王）
        if (rank == levelCardRank) {
            baseScore = 500;
        } else {
            baseScore = RANK_PRIORITY[rank] * 10;
        }

        // 花色加分
        baseScore += SUIT_PRIORITY[suit];

        return baseScore;
    }

    /**
     * 根据点数计算卡牌的分数
     * @param rank 点数
     * @param levelCardRank 级牌点数
     * @return 卡牌分数
     */
    private int calculateCardScoreByRank(int rank, int levelCardRank) {
        // 大小王分数最高
        if (rank == 13) {
            return 1000; // 小王
        } else if (rank == 14) {
            return 2000; // 大王
        }

        // 计算基础分数
        int baseScore = 0;
        
        // 级牌优先级最高（仅次于大小王）
        if (rank == levelCardRank) {
            baseScore = 500;
        } else {
            baseScore = RANK_PRIORITY[rank] * 10;
        }

        return baseScore;
    }

    /**
     * 检查玩家是否为AI
     * @param playerId 玩家ID
     * @return 是否为AI玩家
     */
    public boolean isAIPlayer(String playerId) {
        // 简单判断：如果玩家ID以"ai_"开头，则认为是AI玩家
        return playerId != null && (playerId.startsWith("ai_") || playerId.startsWith("ai_player_"));
    }

    /**
     * 生成AI玩家ID
     * @param index AI索引
     * @return AI玩家ID
     */
    public String generateAIPlayerId(int index) {
        return "ai_player_" + index;
    }

    // ============================================================
    //  自学习权重调整引擎
    // ============================================================

    /**
     * 牌型枚举（用于自学习统计）
     */
    public enum CardPlayType {
        SINGLE("单张"),
        PAIR("对子"),
        TRIPLE("三张"),
        STRAIGHT("顺子"),
        THREE_WITH_TWO("三带二"),
        STEEL_PLATE("钢板"),
        STRAIGHT_FLUSH("同花顺"),
        BOMB("炸弹"),
        PASS("过牌");

        private final String displayName;

        CardPlayType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static CardPlayType fromString(String type) {
            for (CardPlayType t : values()) {
                if (t.displayName.equals(type)) {
                    return t;
                }
            }
            return null;
        }
    }

    /**
     * 牌型统计数据
     */
    public static class CardTypeStatistics {
        /** 出牌次数 */
        private int playCount = 0;
        /** 胜率（该牌型出牌后最终获胜的比例） */
        private double winRate = 0.5;
        /** 获胜次数 */
        private int winCount = 0;
        /** 最近N局的胜率（滑动窗口，反映近期表现） */
        private double recentWinRate = 0.5;
        /** 最近N局的结果记录（true=胜，false=负），最大100条 */
        private final java.util.LinkedList<Boolean> recentResults = new java.util.LinkedList<>();
        /** 最后使用时间戳 */
        private long lastUsedTime = 0;
        /** 最大保留的近期记录数 */
        private static final int MAX_RECENT_RECORDS = 100;

        /**
         * 记录一次出牌结果
         * @param won 是否获胜
         */
        public synchronized void recordOutcome(boolean won) {
            playCount++;
            if (won) {
                winCount++;
            }
            winRate = playCount > 0 ? (double) winCount / playCount : 0.5;

            // 维护滑动窗口
            recentResults.addLast(won);
            if (recentResults.size() > MAX_RECENT_RECORDS) {
                recentResults.removeFirst();
            }

            long wins = recentResults.stream().filter(r -> r).count();
            recentWinRate = recentResults.size() > 0
                    ? (double) wins / recentResults.size() : 0.5;

            lastUsedTime = System.currentTimeMillis();
        }

        /**
         * 获取综合权重得分（用于推荐排序）
         * @return 0.0 ~ 1.0 之间的权重值
         */
        public synchronized double getWeightScore() {
            // 综合权重 = 历史胜率 * 0.4 + 近期胜率 * 0.5 + 使用频率系数 * 0.1
            double historyWeight = winRate * 0.4;
            double recentWeight = recentWinRate * 0.5;
            double frequencyWeight = Math.min(1.0, playCount / 50.0) * 0.1;
            return historyWeight + recentWeight + frequencyWeight;
        }

        public int getPlayCount() { return playCount; }
        public double getWinRate() { return winRate; }
        public double getRecentWinRate() { return recentWinRate; }
        public long getLastUsedTime() { return lastUsedTime; }
    }

    /**
     * 自学习权重调整引擎
     *
     * <p>维护每种牌型的统计信息，基于历史对局数据动态调整出牌推荐权重。
     * 核心思想：提高高胜率牌型的推荐优先级，降低低胜率牌型的优先级。
     */
    public static class LearningWeightEngine {

        /** 牌型 -> 统计数据映射 */
        private final Map<CardPlayType, CardTypeStatistics> statisticsMap = new HashMap<>();

        /** 最近N局整体统计 */
        private final java.util.LinkedList<Boolean> gameResults = new java.util.LinkedList<>();

        /** 最大保留的对局数 */
        private static final int MAX_GAME_RECORDS = 200;

        /** 总对局数 */
        private int totalGames = 0;

        /** 获胜对局数 */
        private int wonGames = 0;

        /** 学习率（0~1），控制新数据对权重的影响程度 */
        private double learningRate = 0.3;

        public LearningWeightEngine() {
            // 初始化所有牌型的统计信息
            for (CardPlayType type : CardPlayType.values()) {
                statisticsMap.put(type, new CardTypeStatistics());
            }
        }

        /**
         * 记录一次出牌结果
         *
         * @param cardType 出的牌型
         * @param won      是否因这次出牌最终获胜
         */
        public synchronized void recordPlayOutcome(String cardType, boolean won) {
            CardPlayType type = CardPlayType.fromString(cardType);
            if (type == null) {
                log.warn("未知牌型: {}，跳过统计", cardType);
                return;
            }
            CardTypeStatistics stats = statisticsMap.get(type);
            if (stats != null) {
                stats.recordOutcome(won);
                log.debug("自学习统计：牌型={}, playCount={}, winRate={}, recentWinRate={}",
                        cardType, stats.getPlayCount(),
                        String.format("%.2f", stats.getWinRate()),
                        String.format("%.2f", stats.getRecentWinRate()));
            }
        }

        /**
         * 记录一局游戏的最终结果
         *
         * @param won 是否获胜
         */
        public synchronized void recordGameResult(boolean won) {
            totalGames++;
            if (won) {
                wonGames++;
            }
            gameResults.addLast(won);
            if (gameResults.size() > MAX_GAME_RECORDS) {
                gameResults.removeFirst();
            }
            log.info("自学习统计：总对局={}, 获胜={}, 总胜率={}",
                    totalGames, wonGames, String.format("%.2f", getOverallWinRate()));
        }

        /**
         * 获取推荐出牌类型列表（按综合权重降序排列）
         *
         * @return 按推荐优先级排序的牌型列表
         */
        public synchronized List<CardPlayType> getRecommendedCardTypes() {
            return statisticsMap.entrySet().stream()
                    .filter(e -> e.getValue().getPlayCount() > 0) // 只推荐有使用记录的牌型
                    .sorted((a, b) -> Double.compare(
                            b.getValue().getWeightScore(),
                            a.getValue().getWeightScore()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        /**
         * 获取特定牌型的推荐分值（用于出牌决策时调整分数）
         *
         * @param cardType 牌型字符串
         * @return 权重调整系数（0.5 ~ 1.5），乘以基础分数后影响出牌优先级
         */
        public synchronized double getWeightAdjustment(String cardType) {
            CardPlayType type = CardPlayType.fromString(cardType);
            if (type == null) {
                return 1.0;
            }
            CardTypeStatistics stats = statisticsMap.get(type);
            if (stats == null || stats.getPlayCount() == 0) {
                return 1.0;
            }
            // 调整系数 = 1.0 + (综合权重 - 0.5) * learningRate * 2
            // 胜率 > 0.5 时系数 > 1.0（提高优先级），反之降低
            double weight = stats.getWeightScore();
            return 1.0 + (weight - 0.5) * learningRate * 2;
        }

        /**
         * 获取整体胜率
         */
        public synchronized double getOverallWinRate() {
            return totalGames > 0 ? (double) wonGames / totalGames : 0.5;
        }

        /**
         * 获取所有牌型的统计摘要
         */
        public synchronized Map<String, Map<String, Object>> getStatisticsSummary() {
            Map<String, Map<String, Object>> summary = new HashMap<>();
            for (Map.Entry<CardPlayType, CardTypeStatistics> entry : statisticsMap.entrySet()) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("playCount", entry.getValue().getPlayCount());
                stat.put("winRate", entry.getValue().getWinRate());
                stat.put("recentWinRate", entry.getValue().getRecentWinRate());
                stat.put("weightScore", entry.getValue().getWeightScore());
                summary.put(entry.getKey().getDisplayName(), stat);
            }
            return summary;
        }

        /**
         * 重置所有统计数据
         */
        public synchronized void reset() {
            for (CardPlayType type : CardPlayType.values()) {
                statisticsMap.put(type, new CardTypeStatistics());
            }
            gameResults.clear();
            totalGames = 0;
            wonGames = 0;
            log.info("自学习权重引擎已重置");
        }

        /**
         * 设置学习率
         */
        public void setLearningRate(double learningRate) {
            if (learningRate >= 0 && learningRate <= 1) {
                this.learningRate = learningRate;
            }
        }

        /**
         * 获取当前学习率
         */
        public double getLearningRate() {
            return learningRate;
        }

        /**
         * 获取统计数据快照（用于外部查询或持久化）
         */
        public Map<CardPlayType, CardTypeStatistics> getStatisticsSnapshot() {
            return new HashMap<>(statisticsMap);
        }
    }

    /**
     * AI自学习权重引擎实例
     */
    private final LearningWeightEngine learningEngine = new LearningWeightEngine();

    /**
     * 获取自学习权重引擎
     */
    public LearningWeightEngine getLearningEngine() {
        return learningEngine;
    }

    /**
     * 集成 bombChainDetector 的增强炸弹查找
     * 在原有 findBomb 基础上，增加对连对炸弹的递归回溯检测
     */
    public List<Integer> findBombWithChain(List<Integer> handCards, int levelCardRank) {
        List<Integer> normalBomb = findBomb(handCards, levelCardRank);
        if (normalBomb != null) return normalBomb;
        GameReferee.BombChainResult chainResult = gameReferee.bombChainDetector(handCards, levelCardRank);
        if (chainResult.isFound()) {
            log.info("AI player chain bomb detected: baseRank={}, chainLength={}", chainResult.getBaseRank(), chainResult.getChainLength());
            return chainResult.getCardIds();
        }
        return null;
    }

    /**
     * 集成 tripleWithTwoDetector 的增强三带二查找
     */
    public List<Integer> findTripleWithTwo(List<Integer> handCards, int levelCardRank) {
        GameReferee.TripleWithTwoResult twResult = gameReferee.tripleWithTwoDetector(handCards, levelCardRank);
        if (twResult.isFound()) {
            log.info("AI player triple+two detected: tripleRank={}, pairRank={}, options={}", twResult.getTripleRank(), twResult.getPairRank(), twResult.getAllOptions().size());
            return twResult.getCardIds();
        }
        return null;
    }

    /**
     * 集成精确判定器的 playFirstCard 增强版本
     */
    public List<Integer> playFirstCardEnhanced(List<Integer> handCards, int levelCardRank) {
        Integer bestSingle = findBestSingleCard(handCards, levelCardRank);
        if (bestSingle != null) {
            List<Integer> c = new ArrayList<>(); c.add(bestSingle); return c;
        }
        List<Integer> bestPair = findBestPair(handCards, levelCardRank);
        if (bestPair != null) return bestPair;
        List<Integer> bestThree = findBestThree(handCards, levelCardRank);
        if (bestThree != null) return bestThree;
        List<Integer> bestStraight = findBestStraight(handCards, levelCardRank);
        if (bestStraight != null) return bestStraight;
        List<Integer> tw = findTripleWithTwo(handCards, levelCardRank);
        if (tw != null) return tw;
        List<Integer> cb = findBombWithChain(handCards, levelCardRank);
        if (cb != null) return cb;
        return null;
    }

    /**
     * 使用自学习权重调整的出牌决策
     *
     * <p>在原有出牌逻辑基础上，加入自学习权重调整：
     * 对历史胜率高的牌型给予更高的优先级，胜率低的牌型降低优先级。
     *
     * @param gameRoom      游戏房间
     * @param playerId      玩家ID
     * @param levelCardRank 级牌点数
     * @return 出牌决策（含权重调整后的推荐）
     */
    public List<Integer> playCardsWithLearning(GameRoom gameRoom, String playerId, int levelCardRank) {
        List<Integer> result = playCards(gameRoom, playerId, levelCardRank);
        if (result != null && !result.isEmpty()) {
            String cardType = CardUtils.getCardType(result, levelCardRank);
            if (cardType != null) {
                double adjustment = learningEngine.getWeightAdjustment(cardType);
                log.debug("AI玩家 {} 出牌类型={}, 权重调整系数={}",
                        playerId, cardType, String.format("%.2f", adjustment));
            }
        }
        return result;
    }

    /**
     * 获取按自学习权重排序的出牌推荐
     *
     * @param gameRoom      游戏房间
     * @param playerId      玩家ID
     * @param levelCardRank 级牌点数
     * @return 按推荐优先级排序的出牌方案列表（每种牌型一个方案）
     */
    public List<List<Integer>> getWeightedRecommendations(GameRoom gameRoom, String playerId, int levelCardRank) {
        List<List<Integer>> recommendations = new ArrayList<>();
        List<Integer> handCards = gameRoom.getPlayerHandCards(playerId);
        if (handCards == null || handCards.isEmpty()) {
            return recommendations;
        }

        // 收集所有可能的出牌方案
        Map<String, List<Integer>> cardTypeToPlay = new HashMap<>();

        // 自由出牌场景
        String lastCardType = gameRoom.getLastCardType();
        if (lastCardType == null || playerId.equals(gameRoom.getLastPlayerId())) {
            Integer bestSingle = findBestSingleCard(handCards, levelCardRank);
            if (bestSingle != null) {
                List<Integer> single = new ArrayList<>();
                single.add(bestSingle);
                cardTypeToPlay.put("单张", single);
            }
            List<Integer> pair = findBestPair(handCards, levelCardRank);
            if (pair != null) cardTypeToPlay.put("对子", pair);
            List<Integer> triple = findBestThree(handCards, levelCardRank);
            if (triple != null) cardTypeToPlay.put("三张", triple);
            List<Integer> straight = findBestStraight(handCards, levelCardRank);
            if (straight != null) cardTypeToPlay.put("顺子", straight);
        }

        // 根据自学习权重排序
        List<CardPlayType> recommended = learningEngine.getRecommendedCardTypes();
        for (CardPlayType type : recommended) {
            String typeName = type.getDisplayName();
            if (cardTypeToPlay.containsKey(typeName)) {
                recommendations.add(cardTypeToPlay.get(typeName));
            }
        }

        // 补充未在推荐列表中的可选方案
        for (Map.Entry<String, List<Integer>> entry : cardTypeToPlay.entrySet()) {
            if (recommendations.stream().noneMatch(r -> r.equals(entry.getValue()))) {
                recommendations.add(entry.getValue());
            }
        }

        log.debug("AI玩家 {} 加权推荐：共 {} 个方案", playerId, recommendations.size());
        return recommendations;
    }
}