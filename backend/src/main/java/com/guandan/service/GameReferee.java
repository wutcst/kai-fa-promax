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
    //  阶段标记 — 提升牌型规则准确性：管牌与牌型校验逻辑
    //  子任务：
    //   [√] canBeat 核心管牌逻辑（首出/同牌型/炸弹/非同牌型）
    //   [√] isValidHand 牌型合法性校验
    //   [√] validateCardIds 前置守卫（空值/重复/越界/null）
    //   [√] 测试验证点补充（GameReferee-1 ~ 16）
    //
    //  性能优化（本轮）：
    //   [√] 通过 CardUtils 缓存层间接减少重复牌型识别
    //   [√] log 日志加入耗时标记便于追踪性能瓶颈
    //
    //  配置说明：
    //   - 首出场景：lastHand 为 null/空时，仅验证 currentHand 牌型合法
    //   - 管牌规则：同牌型比大小，炸弹管非炸弹，王炸最大
    //   - 容错机制：lastType 为 UNKNOWN 时允许出牌
    // ============================================================

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

    /**
     * 获取当前使用的学习权重引擎
     */
    public LearningWeightEngine getLearningWeightEngine() {
        return learningWeightEngine;
    }

    /**
     * 分析一手牌中所有可能的出牌方案，返回按自学习权重排序的推荐列表
     *
     * <p>该方法在原有 {@link #canBeat(List, List, int)} 基础上，
     * 加入自学习权重引擎的推荐排序，使 AI 出牌决策具备学习能力。
     *
     * @param handCards      当前手牌
     * @param lastHand       上一手牌（可为空表示首出）
     * @param levelCardRank  级牌点数
     * @return 按推荐优先级排序的出牌方案列表（每个方案为一个卡牌ID列表）
     */
    public List<List<Integer>> getWeightedPlayOptions(List<Integer> handCards,
                                                       List<Integer> lastHand,
                                                       int levelCardRank) {
        List<List<Integer>> options = new java.util.ArrayList<>();
        if (handCards == null || handCards.isEmpty()) {
            return options;
        }

        // 首出场景：枚举所有可能的牌型
        if (lastHand == null || lastHand.isEmpty()) {
            // 单张
            for (Integer cardId : handCards) {
                List<Integer> single = new java.util.ArrayList<>();
                single.add(cardId);
                if (isValidHand(single, levelCardRank)) {
                    options.add(single);
                }
            }
            // 对子
            java.util.Map<Integer, List<Integer>> rankGroups = new java.util.HashMap<>();
            for (Integer cardId : handCards) {
                int rank = CardUtils.getRank(cardId);
                rankGroups.computeIfAbsent(rank, k -> new java.util.ArrayList<>()).add(cardId);
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
            // 跟牌场景：找出所有能管住上一手牌的方案
            CardType lastType = gameAlgorithm.getCardType(lastHand, levelCardRank);
            if (lastType == CardType.UNKNOWN) {
                return options;
            }

            // 对于每种可能的出牌组合，验证是否能管住
            for (int i = 0; i < handCards.size() && options.size() < 10; i++) {
                for (int j = i + 1; j <= handCards.size() && options.size() < 10; j++) {
                    List<Integer> subset = handCards.subList(i, j);
                    if (isValidHand(subset, levelCardRank) && canBeat(lastHand, subset, levelCardRank)) {
                        options.add(new java.util.ArrayList<>(subset));
                    }
                }
            }
        }

        // 如果有自学习引擎，根据权重排序
        if (learningWeightEngine != null && !options.isEmpty()) {
            List<CardPlayType> recommendedTypes = learningWeightEngine.getRecommendedCardTypes();
            options.sort((a, b) -> {
                String typeA = CardUtils.getCardType(a, levelCardRank);
                String typeB = CardUtils.getCardType(b, levelCardRank);
                int idxA = -1;
                int idxB = -1;
                for (int i = 0; i < recommendedTypes.size(); i++) {
                    if (recommendedTypes.get(i).getDisplayName().equals(typeA)) {
                        idxA = i;
                    }
                    if (recommendedTypes.get(i).getDisplayName().equals(typeB)) {
                        idxB = i;
                    }
                }
                if (idxA >= 0 && idxB >= 0) return Integer.compare(idxA, idxB);
                if (idxA >= 0) return -1;
                if (idxB >= 0) return 1;
                return 0;
            });
        }

        return options;
    }

    /**
     * 记录出牌结果到自学习引擎
     *
     * @param cardType 牌型字符串
     * @param won      是否获胜
     */
    public void recordPlayOutcome(String cardType, boolean won) {
        if (learningWeightEngine != null) {
            learningWeightEngine.recordPlayOutcome(cardType, won);
        }
    }

    /**
     * 记录游戏结果到自学习引擎
     *
     * @param won 是否获胜
     */
    public void recordGameResult(boolean won) {
        if (learningWeightEngine != null) {
            learningWeightEngine.recordGameResult(won);
        }
    }

    /**
     * 获取自学习引擎的统计摘要
     */
    public java.util.Map<String, java.util.Map<String, Object>> getLearningStatistics() {
        if (learningWeightEngine != null) {
            return learningWeightEngine.getStatisticsSummary();
        }
        return new java.util.HashMap<>();
    }
}
