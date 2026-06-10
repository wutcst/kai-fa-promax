package com.guandan.service;

import com.guandan.model.CardType;
import com.guandan.game.util.CardUtils;
import com.guandan.game.service.GameAlgorithm;
import com.guandan.game.service.AIService.CardPlayType;
import com.guandan.game.service.AIService.CardTypeStatistics;
import com.guandan.game.service.AIService.LearningWeightEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 游戏裁判服务
 * 负责比牌逻辑：判断当前手牌是否能管住上一手牌
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>牌型合法性校验 — 判断一手牌是否符合牌型规则</li>
 *   <li>管牌判定 — 判断当前出牌是否能管住上一手牌</li>
 *   <li>卡牌ID前置守卫 — 空值检查、重复提交、边界校验</li>
 * </ul>
 *
 * <p><b>管牌规则：</b>
 * <ul>
 *   <li>无上一手牌（首出）→ 任何合法牌型均可出</li>
 *   <li>同牌型且张数相同 → 比较牌值大小</li>
 *   <li>炸弹可管非炸弹牌型</li>
 *   <li>王炸最大</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>currentHand 为 null/空 → 返回 false</li>
 *   <li>lastHand 为 null/空（首出）→ 验证 currentHand 牌型合法即可</li>
 *   <li>currentType 为 UNKNOWN → 返回 false</li>
 *   <li>lastType 为 UNKNOWN → 允许出牌（容错）</li>
 *   <li>cardIds 包含重复或越界 ID → 返回 false</li>
 * </ul>
 *
 * <p><b>新增精确判定器：</b>
 * <ul>
 *   <li>bombChainDetector — 递归回溯查找连对炸弹（如 33 44 55 66）</li>
 *   <li>tripleWithTwoDetector — 精确判定多组三带二可选项（如 333 44 或 444 55）</li>
 * </ul>
 */
@Slf4j
@Service
public class GameReferee {

    @Autowired
    private GameAlgorithm gameAlgorithm;

    /**
     * 判断当前手牌是否能管住上一手牌
     *
     * @param lastHand 上一手牌（卡牌ID列表），如果为null或空表示没有上一手牌（首出）
     * @param currentHand 当前手牌（卡牌ID列表）
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 如果能管住返回true，否则返回false
     */
    public boolean canBeat(List<Integer> lastHand, List<Integer> currentHand, int levelCardRank) {
        log.info("canBeat入口: lastHand={}, currentHand={}, levelCardRank={}",
                lastHand != null ? Arrays.toString(CardUtils.idsToStrings(lastHand.stream().mapToInt(i -> i).toArray())) : "无",
                Arrays.toString(CardUtils.idsToStrings(currentHand.stream().mapToInt(i -> i).toArray())),
                CardUtils.getRankName(levelCardRank));

        // 参数空值检查
        if (currentHand == null || currentHand.isEmpty()) {
            log.warn("当前出牌为空，无法出牌");
            return false;
        }

        // 如果没有上一手牌，任何合法牌型都可以出
        if (lastHand == null || lastHand.isEmpty()) {
            CardType currentType = gameAlgorithm.getCardType(currentHand, levelCardRank);
            return currentType != CardType.UNKNOWN;
        }

        // 识别两手牌的牌型
        CardType lastType = gameAlgorithm.getCardType(lastHand, levelCardRank);
        CardType currentType = gameAlgorithm.getCardType(currentHand, levelCardRank);
        log.info("canBeat牌型: lastType={}, currentType={}", lastType, currentType);

        // 如果当前牌型不合法，不能出
        if (currentType == CardType.UNKNOWN) {
            log.warn("当前手牌牌型不合法");
            return false;
        }

        // 如果上一手牌型不合法（理论上不应该发生），允许出牌
        if (lastType == CardType.UNKNOWN) {
            log.warn("上一手牌型不合法，允许出牌");
            return true;
        }

        // 使用GameAlgorithm进行比较
        return gameAlgorithm.compareCards(currentHand, lastHand, levelCardRank);
    }

    /**
     * 验证牌型是否合法
     *
     * @param cardIds 卡牌ID列表
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 如果合法返回true，否则返回false
     */
    public boolean isValidHand(List<Integer> cardIds, int levelCardRank) {
        // 空值和边界校验（提取为核心判断前置守卫）
        if (!validateCardIds(cardIds)) {
            return false;
        }

        CardType type = gameAlgorithm.getCardType(cardIds, levelCardRank);
        if (type == CardType.UNKNOWN) {
            log.warn("牌型验证失败：无法识别的牌型，卡牌数量={}", cardIds.size());
        }
        return type != CardType.UNKNOWN;
    }

    /**
     * 验证卡牌ID列表的合法性（空值检查、重复提交、边界校验）
     *
     * @param cardIds 卡牌ID列表
     * @return 如果合法返回true
     */
    private boolean validateCardIds(List<Integer> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            log.warn("牌型验证失败：卡牌列表为空");
            return false;
        }

        // 重复提交检查：同一张卡牌ID不能出现两次
        if (cardIds.stream().distinct().count() != cardIds.size()) {
            log.warn("牌型验证失败：卡牌列表包含重复ID");
            return false;
        }

        // 卡牌ID边界校验
        for (Integer id : cardIds) {
            if (id == null || id < 0 || id > 107) {
                log.warn("牌型验证失败：包含非法卡牌ID {}", id);
                return false;
            }
        }

        return true;
    }

    // ============================================================
    //  炸弹连对判定器（bombChainDetector）
    //  基于递归回溯算法查找所有可能的连对炸弹
    //  连对炸弹定义：至少 3 个连续点数的对子（如 33 44 55 66）
    //  每个对子 2 张牌，总张数为 2 * n，n >= 3
    // ============================================================

    /**
     * 连对炸弹检测结果
     */
    public static class BombChainResult {
        /** 是否找到合法的连对炸弹 */
        private boolean found;
        /** 连对的起始点数（最小的点数） */
        private int baseRank;
        /** 连续对子的数量 */
        private int chainLength;
        /** 连对炸弹包含的卡牌ID列表 */
        private List<Integer> cardIds;
        /** 所有连对炸弹方案（按长度降序排列） */
        private List<List<Integer>> allChains;

        public BombChainResult() {
            this.found = false;
            this.baseRank = -1;
            this.chainLength = 0;
            this.cardIds = new ArrayList<>();
            this.allChains = new ArrayList<>();
        }

        public boolean isFound() { return found; }
        public void setFound(boolean found) { this.found = found; }
        public int getBaseRank() { return baseRank; }
        public void setBaseRank(int baseRank) { this.baseRank = baseRank; }
        public int getChainLength() { return chainLength; }
        public void setChainLength(int chainLength) { this.chainLength = chainLength; }
        public List<Integer> getCardIds() { return cardIds; }
        public void setCardIds(List<Integer> cardIds) { this.cardIds = cardIds; }
        public List<List<Integer>> getAllChains() { return allChains; }
        public void setAllChains(List<List<Integer>> allChains) { this.allChains = allChains; }
    }

    /**
     * 递归回溯查找连对炸弹
     * 从手牌中找出所有长度 >= 3 的连续对子序列
     *
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return BombChainResult — 包含所有连对方案
     */
    public BombChainResult bombChainDetector(List<Integer> handCards, int levelCardRank) {
        BombChainResult result = new BombChainResult();

        if (handCards == null || handCards.size() < 6) {
            return result; // 连对至少需要 6 张牌（3 个对子）
        }

        // 按点数分组
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            // 跳过大小王和 2（连对中不能包含 2 和王）
            if (rank >= 12) continue;
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 找出所有至少有一对（>= 2 张）的点数
        List<Integer> pairRanks = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            if (entry.getValue().size() >= 2) {
                pairRanks.add(entry.getKey());
            }
        }
        Collections.sort(pairRanks);
        if (pairRanks.size() < 3) return result;

        // 递归回溯：找连续点数序列
        List<List<Integer>> allChains = new ArrayList<>();
        findConsecutivePairs(pairRanks, 0, new ArrayList<>(), rankToCards, allChains);

        if (allChains.isEmpty()) return result;

        // 按链长度降序排列，选最长的
        allChains.sort((a, b) -> Integer.compare(b.size(), a.size()));
        List<Integer> bestChain = allChains.get(0);

        List<Integer> chainCardIds = new ArrayList<>();
        for (Integer rank : bestChain) {
            chainCardIds.addAll(rankToCards.get(rank).subList(0, 2));
        }

        result.setFound(true);
        result.setBaseRank(bestChain.get(0));
        result.setChainLength(bestChain.size());
        result.setCardIds(chainCardIds);
        result.setAllChains(allChains.stream()
                .map(chain -> {
                    List<Integer> ids = new ArrayList<>();
                    for (Integer r : chain) {
                        ids.addAll(rankToCards.get(r).subList(0, 2));
                    }
                    return ids;
                })
                .collect(Collectors.toList()));

        log.info("连对炸弹检测: found={}, baseRank={}, chainLength={}, cardCount={}",
                result.isFound(), result.getBaseRank(), result.getChainLength(), result.getCardIds().size());
        return result;
    }

    /**
     * 递归回溯：在排序后的点数列表中找连续序列
     * @param sortedRanks 排序后的点数列表
     * @param start 当前起始索引
     * @param current 当前正在构建的序列
     * @param rankToCards 点数到卡牌的映射
     * @param allChains 所有找到的连对序列
     */
    private void findConsecutivePairs(List<Integer> sortedRanks, int start,
                                       List<Integer> current,
                                       Map<Integer, List<Integer>> rankToCards,
                                       List<List<Integer>> allChains) {
        // 如果当前序列长度 >= 3，保存
        if (current.size() >= 3) {
            allChains.add(new ArrayList<>(current));
        }

        for (int i = start; i < sortedRanks.size(); i++) {
            int rank = sortedRanks.get(i);
            // 检查连续性：如果当前序列为空，任何点数都可以作为起点
            if (current.isEmpty()) {
                current.add(rank);
                findConsecutivePairs(sortedRanks, i + 1, current, rankToCards, allChains);
                current.remove(current.size() - 1);
            } else {
                // 必须连续 rank + 1 == current.last + 1
                int lastRank = current.get(current.size() - 1);
                if (rank == lastRank + 1) {
                    current.add(rank);
                    findConsecutivePairs(sortedRanks, i + 1, current, rankToCards, allChains);
                    current.remove(current.size() - 1);
                } else if (rank > lastRank + 1) {
                    // 因为已排序，更大的点数只会差距更大，剪枝
                    break;
                }
            }
        }
    }

    // ============================================================
    //  三带二精确判定器（tripleWithTwoDetector）
    //  基于分组和回溯获取所有合法的三带二组合
    //  三带二定义：3 张同点数 + 2 张同点数（不同于三张的点数）
    // ============================================================

    /**
     * 三带二检测结果
     */
    public static class TripleWithTwoResult {
        /** 是否找到合法的三带二 */
        private boolean found;
        /** 三张的点数 */
        private int tripleRank;
        /** 对子的点数 */
        private int pairRank;
        /** 三带二包含的卡牌ID列表 */
        private List<Integer> cardIds;
        /** 所有三带二方案（按三张点数降序排列） */
        private List<List<Integer>> allOptions;

        public TripleWithTwoResult() {
            this.found = false;
            this.tripleRank = -1;
            this.pairRank = -1;
            this.cardIds = new ArrayList<>();
            this.allOptions = new ArrayList<>();
        }

        public boolean isFound() { return found; }
        public void setFound(boolean found) { this.found = found; }
        public int getTripleRank() { return tripleRank; }
        public void setTripleRank(int tripleRank) { this.tripleRank = tripleRank; }
        public int getPairRank() { return pairRank; }
        public void setPairRank(int pairRank) { this.pairRank = pairRank; }
        public List<Integer> getCardIds() { return cardIds; }
        public void setCardIds(List<Integer> cardIds) { this.cardIds = cardIds; }
        public List<List<Integer>> getAllOptions() { return allOptions; }
        public void setAllOptions(List<List<Integer>> allOptions) { this.allOptions = allOptions; }
    }

    /**
     * 三带二精确判定器
     * 从手牌中找出所有合法的三带二组合
     *
     * @param handCards 手牌列表
     * @param levelCardRank 级牌点数
     * @return TripleWithTwoResult — 包含所有三带二方案
     */
    public TripleWithTwoResult tripleWithTwoDetector(List<Integer> handCards, int levelCardRank) {
        TripleWithTwoResult result = new TripleWithTwoResult();

        if (handCards == null || handCards.size() < 5) {
            return result;
        }

        // 按点数分组
        Map<Integer, List<Integer>> rankToCards = new HashMap<>();
        for (Integer cardId : handCards) {
            int rank = CardUtils.getRank(cardId);
            rankToCards.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
        }

        // 查找所有点数 >= 3 的三张候选
        List<Integer> tripleCandidates = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            if (entry.getValue().size() >= 3) {
                tripleCandidates.add(entry.getKey());
            }
        }
        if (tripleCandidates.isEmpty()) return result;

        // 查找所有点数 >= 2 的对子候选
        List<Integer> pairCandidates = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rankToCards.entrySet()) {
            if (entry.getValue().size() >= 2) {
                pairCandidates.add(entry.getKey());
            }
        }
        if (pairCandidates.isEmpty()) return result;

        // 枚举所有三张 + 对子组合，要求不同点数
        List<List<Integer>> allOptions = new ArrayList<>();
        for (Integer tRank : tripleCandidates) {
            for (Integer pRank : pairCandidates) {
                if (!tRank.equals(pRank)) {
                    List<Integer> option = new ArrayList<>();
                    option.addAll(rankToCards.get(tRank).subList(0, 3));
                    option.addAll(rankToCards.get(pRank).subList(0, 2));
                    allOptions.add(option);
                }
            }
        }

        if (allOptions.isEmpty()) return result;

        // 按三张点数降序排列
        allOptions.sort((a, b) -> {
            int rankA = CardUtils.getRank(a.get(0));
            int rankB = CardUtils.getRank(b.get(0));
            return Integer.compare(rankB, rankA);
        });

        List<Integer> bestOption = allOptions.get(0);
        int bestTripleRank = CardUtils.getRank(bestOption.get(0));
        // 找到对子的点数（5 张牌中不同于三张点数的那个）
        int bestPairRank = -1;
        for (int i = 3; i < 5; i++) {
            int pr = CardUtils.getRank(bestOption.get(i));
            if (pr != bestTripleRank) {
                bestPairRank = pr;
                break;
            }
        }

        result.setFound(true);
        result.setTripleRank(bestTripleRank);
        result.setPairRank(bestPairRank);
        result.setCardIds(bestOption);
        result.setAllOptions(allOptions);

        log.info("三带二检测: found={}, tripleRank={}, pairRank={}, options={}",
                result.isFound(), result.getTripleRank(), result.getPairRank(), allOptions.size());
        return result;
    }

    // ============================================================
    //  自学习权重引擎集成方法
    // ============================================================

    /**
     * 注入的自学习权重引擎（可选）
     */
    private LearningWeightEngine learningWeightEngine;

    /**
     * 设置学习权重引擎（可选注入）
     */
    @Autowired(required = false)
    public void setLearningWeightEngine(LearningWeightEngine engine) {
        this.learningWeightEngine = engine;
    }

    // ============================================================
    //  牌型校验责任链（Chain of Responsibility）
    // ============================================================

    /**
     * 牌型校验器接口
     */
    public interface CardValidator {
        void setNext(CardValidator next);
        CardType validate(List<Integer> cardIds, int levelCardRank);
        CardValidator getNext();
    }

    public static class SingleValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() == 1) {
                return CardType.SINGLE;
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class PairValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() == 2) {
                Map<Integer, Integer> rankCount = new HashMap<>();
                for (Integer id : cardIds) {
                    int rank = CardUtils.getRank(id);
                    rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
                }
                if (rankCount.size() == 1) {
                    return CardType.PAIR;
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class TripleValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() == 3) {
                Map<Integer, Integer> rankCount = new HashMap<>();
                for (Integer id : cardIds) {
                    int rank = CardUtils.getRank(id);
                    rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
                }
                if (rankCount.size() == 1) {
                    return CardType.TRIPLET;
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class StraightValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() >= 5) {
                List<Integer> ranks = cardIds.stream()
                        .map(CardUtils::getRank)
                        .sorted()
                        .distinct()
                        .collect(Collectors.toList());
                if (ranks.stream().anyMatch(r -> r >= 13 || r == 12)) {
                    return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
                }
                boolean consecutive = true;
                for (int i = 1; i < ranks.size(); i++) {
                    if (ranks.get(i) != ranks.get(i - 1) + 1) {
                        consecutive = false;
                        break;
                    }
                }
                if (consecutive && ranks.size() == cardIds.size()) {
                    return CardType.STRAIGHT;
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class ThreeWithTwoValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() == 5) {
                Map<Integer, Integer> rankCount = new HashMap<>();
                for (Integer id : cardIds) {
                    int rank = CardUtils.getRank(id);
                    rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
                }
                if (rankCount.size() == 2 &&
                        rankCount.values().stream().anyMatch(c -> c == 3) &&
                        rankCount.values().stream().anyMatch(c -> c == 2)) {
                    return CardType.TRIPLET_WITH_TWO;
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class SteelPlateValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() == 6) {
                Map<Integer, Integer> rankCount = new HashMap<>();
                for (Integer id : cardIds) {
                    int rank = CardUtils.getRank(id);
                    rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
                }
                if (rankCount.size() == 2 && rankCount.values().stream().allMatch(c -> c == 3)) {
                    List<Integer> ranks = new ArrayList<>(rankCount.keySet());
                    Collections.sort(ranks);
                    if (ranks.get(1) == ranks.get(0) + 1) {
                        return CardType.TRIPLET_STRAIGHT;
                    }
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class StraightFlushValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() == 5) {
                List<Integer> ranks = cardIds.stream()
                        .map(CardUtils::getRank)
                        .sorted()
                        .distinct()
                        .collect(Collectors.toList());
                if (ranks.stream().anyMatch(r -> r >= 13 || r == 12)) {
                    return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
                }
                boolean consecutive = true;
                for (int i = 1; i < ranks.size(); i++) {
                    if (ranks.get(i) != ranks.get(i - 1) + 1) {
                        consecutive = false;
                        break;
                    }
                }
                if (!consecutive || ranks.size() != cardIds.size()) {
                    return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
                }
                int suit = CardUtils.getSuit(cardIds.get(0));
                if (suit >= 0 && cardIds.stream().allMatch(id -> CardUtils.getSuit(id) == suit)) {
                    return CardType.FLUSH_STRAIGHT;
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    public static class BombValidator implements CardValidator {
        private CardValidator next;
        @Override public void setNext(CardValidator next) { this.next = next; }
        @Override public CardValidator getNext() { return next; }
        @Override
        public CardType validate(List<Integer> cardIds, int levelCardRank) {
            if (cardIds.size() >= 4) {
                Map<Integer, Integer> rankCount = new HashMap<>();
                for (Integer id : cardIds) {
                    int rank = CardUtils.getRank(id);
                    rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
                }
                if (rankCount.values().stream().anyMatch(c -> c >= 4)) {
                    return CardType.SMALL_BOMB;
                }
            }
            return next != null ? next.validate(cardIds, levelCardRank) : CardType.UNKNOWN;
        }
    }

    private CardValidator validatorChainHead;

    private CardValidator buildDefaultValidatorChain() {
        CardValidator single = new SingleValidator();
        CardValidator pair = new PairValidator();
        CardValidator triple = new TripleValidator();
        CardValidator straight = new StraightValidator();
        CardValidator threeWithTwo = new ThreeWithTwoValidator();
        CardValidator steelPlate = new SteelPlateValidator();
        CardValidator straightFlush = new StraightFlushValidator();
        CardValidator bomb = new BombValidator();

        single.setNext(pair);
        pair.setNext(triple);
        triple.setNext(straight);
        straight.setNext(threeWithTwo);
        threeWithTwo.setNext(steelPlate);
        steelPlate.setNext(straightFlush);
        straightFlush.setNext(bomb);

        return single;
    }

    public CardType validateByChain(List<Integer> cardIds, int levelCardRank) {
        if (cardIds == null || cardIds.isEmpty()) {
            return CardType.UNKNOWN;
        }
        if (validatorChainHead == null) {
            validatorChainHead = buildDefaultValidatorChain();
        }
        return validatorChainHead.validate(cardIds, levelCardRank);
    }

    public boolean isValidHandByChain(List<Integer> cardIds, int levelCardRank) {
        if (!validateCardIds(cardIds)) {
            return false;
        }
        CardType type = validateByChain(cardIds, levelCardRank);
        if (type == CardType.UNKNOWN) {
            log.warn("责任链校验失败：无法识别的牌型，卡牌数量={}", cardIds.size());
        }
        return type != CardType.UNKNOWN;
    }

    public CardValidator getValidatorChainHead() {
        if (validatorChainHead == null) {
            validatorChainHead = buildDefaultValidatorChain();
        }
        return validatorChainHead;
    }

    public void setValidatorChain(CardValidator newHead) {
        this.validatorChainHead = newHead;
        log.info("牌型校验链已替换");
    }

    public LearningWeightEngine getLearningWeightEngine() {
        return learningWeightEngine;
    }

    public List<List<Integer>> getWeightedPlayOptions(List<Integer> handCards,
                                                       List<Integer> lastHand,
                                                       int levelCardRank) {
        List<List<Integer>> options = new ArrayList<>();
        if (handCards == null || handCards.isEmpty()) {
            return options;
        }

        if (lastHand == null || lastHand.isEmpty()) {
            for (Integer cardId : handCards) {
                List<Integer> single = new ArrayList<>();
                single.add(cardId);
                if (isValidHand(single, levelCardRank)) {
                    options.add(single);
                }
            }
            Map<Integer, List<Integer>> rankGroups = new HashMap<>();
            for (Integer cardId : handCards) {
                int rank = CardUtils.getRank(cardId);
                rankGroups.computeIfAbsent(rank, k -> new ArrayList<>()).add(cardId);
            }
            for (List<Integer> group : rankGroups.values()) {
                if (group.size() >= 2) {
                    options.add(group.subList(0, 2));
                }
                if (group.size() >= 3) {
                    options.add(group.subList(0, 3));
                }
            }
        } else {
            CardType lastType = gameAlgorithm.getCardType(lastHand, levelCardRank);
            if (lastType == CardType.UNKNOWN) {
                return options;
            }
            for (int i = 0; i < handCards.size() && options.size() < 10; i++) {
                for (int j = i + 1; j <= handCards.size() && options.size() < 10; j++) {
                    List<Integer> subset = handCards.subList(i, j);
                    if (isValidHand(subset, levelCardRank) && canBeat(lastHand, subset, levelCardRank)) {
                        options.add(new ArrayList<>(subset));
                    }
                }
            }
        }

        if (learningWeightEngine != null && !options.isEmpty()) {
            List<CardPlayType> recommendedTypes = learningWeightEngine.getRecommendedCardTypes();
            options.sort((a, b) -> {
                String typeA = CardUtils.getCardType(a, levelCardRank);
                String typeB = CardUtils.getCardType(b, levelCardRank);
                int idxA = -1, idxB = -1;
                for (int i = 0; i < recommendedTypes.size(); i++) {
                    if (recommendedTypes.get(i).getDisplayName().equals(typeA)) idxA = i;
                    if (recommendedTypes.get(i).getDisplayName().equals(typeB)) idxB = i;
                }
                if (idxA >= 0 && idxB >= 0) return Integer.compare(idxA, idxB);
                if (idxA >= 0) return -1;
                if (idxB >= 0) return 1;
                return 0;
            });
        }

        return options;
    }

    public void recordPlayOutcome(String cardType, boolean won) {
        if (learningWeightEngine != null) {
            learningWeightEngine.recordPlayOutcome(cardType, won);
        }
    }

    public void recordGameResult(boolean won) {
        if (learningWeightEngine != null) {
            learningWeightEngine.recordGameResult(won);
        }
    }

    public Map<String, Map<String, Object>> getLearningStatistics() {
        if (learningWeightEngine != null) {
            return learningWeightEngine.getStatisticsSummary();
        }
        return new HashMap<>();
    }
}
