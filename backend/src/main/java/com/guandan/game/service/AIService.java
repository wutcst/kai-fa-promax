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
}