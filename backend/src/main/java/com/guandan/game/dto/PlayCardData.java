package com.guandan.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 客户端发送的出牌消息数据
 * 负责人：成员A（核心引擎与逻辑）
 *
 * <p>封装客户端出牌请求的核心字段，包括卡牌列表和过牌判定。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *   <li>cards — 要出的卡牌ID列表（List&lt;Integer&gt;），空列表或 null 表示过牌</li>
 * </ul>
 *
 * <p><b>出牌/过牌判定：</b>
 * <ul>
 *   <li>isPass() == true 且 cards 为空 — 表示玩家选择过牌</li>
 *   <li>isPass() == false — 表示玩家正常出牌，走牌型校验和管牌判定</li>
 *   <li>getSafeCards() — 返回非空卡牌列表，null 时返回空列表，避免 NPE</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>cards 包含重复 ID — GameReferee.validateCardIds 拦截</li>
 *   <li>cards 包含越界 ID（&lt;0 或 &gt;107）— GameReferee.validateCardIds 拦截</li>
 *   <li>非当前玩家回合 — GameController 拒绝操作</li>
 *   <li>游戏未开始或已结束 — GameController 拒绝操作</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayCardData {
    /**
     * 要出的卡牌ID列表
     */
    private List<Integer> cards;

    /**
     * 检查是否为过牌（空列表或null表示过牌）
     * @return true 如果不出牌
     */
    public boolean isPass() {
        return cards == null || cards.isEmpty();
    }

    /**
     * 获取安全的卡牌列表（非空）
     * @return 非空卡牌列表，null时返回空列表
     */
    public List<Integer> getSafeCards() {
        return cards != null ? cards : new java.util.ArrayList<>();
    }

    // ============================================================
    //  回归验证点：出牌/过牌数据
    // ============================================================
    //
    //  1. cards 为空列表  -> isPass() == true, getSafeCards() 返回空列表
    //  2. cards 为 null   -> isPass() == true, getSafeCards() 返回空列表（防 NPE）
    //  3. cards 有牌      -> isPass() == false
    //  4. cards 包含重复 ID -> 服务层 GameReferee 校验拦截
    //  5. cards 中 ID < 0 或 > 107 -> 服务层 GameReferee 校验拦截
}
