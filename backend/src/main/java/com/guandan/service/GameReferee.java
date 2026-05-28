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
     * 验证牌型是否合法（含重复提交和空值拦截）
     *
     * <p><b>检查规则：</b>
     * <ul>
     *   <li>cardIds 为 null 或空 → 不合法</li>
     *   <li>cardIds 包含重复卡牌ID（同一张牌出现多次）→ 不合法（防重复提交）</li>
     *   <li>cardIds 中包含越界ID（<0 或 >107）→ 不合法</li>
     *   <li>通过 GameAlgorithm 识别牌型 → UNKNOWN 则不合法</li>
     * </ul>
     *
     * @param cardIds 卡牌ID列表
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 如果合法返回true，否则返回false
     */
    public boolean isValidHand(List<Integer> cardIds, int levelCardRank) {
        // 空值检查
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

        CardType type = gameAlgorithm.getCardType(cardIds, levelCardRank);
        if (type == CardType.UNKNOWN) {
            log.warn("牌型验证失败：无法识别的牌型，卡牌数量={}", cardIds.size());
        }
        return type != CardType.UNKNOWN;
    }
}
