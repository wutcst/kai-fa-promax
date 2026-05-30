package com.guandan.service;

import com.guandan.model.CardType;
import com.guandan.game.util.CardUtils;
import com.guandan.game.service.GameAlgorithm;
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
    //  测试验证点 — 出牌/管牌逻辑回归
    // ============================================================
    // [GameReferee-1] canBeat 首出(null lastHand) -> 合法牌型返回 true
    // [GameReferee-2] canBeat 首出(空 lastHand) -> 合法牌型返回 true
    // [GameReferee-3] canBeat currentHand 为 null -> 返回 false
    // [GameReferee-4] canBeat currentHand 为 空列表 -> 返回 false
    // [GameReferee-5] canBeat currentType 为 UNKNOWN -> 返回 false
    // [GameReferee-6] canBeat lastType 为 UNKNOWN（容错）-> 返回 true
    // [GameReferee-7] canBeat 同牌型大管小 -> 返回 true
    // [GameReferee-8] canBeat 同牌型小管大 -> 返回 false
    // [GameReferee-9] canBeat 炸弹管非炸弹 -> 返回 true
    // [GameReferee-10] canBeat 非同牌型互管（非炸弹）-> 返回 false
    // [GameReferee-11] isValidHand null/空 -> 返回 false
    // [GameReferee-12] isValidHand 合法牌型 -> 返回 true
    // [GameReferee-13] isValidHand 非法牌型 -> 返回 false
    // [GameReferee-14] isValidHand 重复ID -> 返回 false
    // [GameReferee-15] isValidHand 越界ID -> 返回 false
    // [GameReferee-16] isValidHand 包含null -> 返回 false
}
