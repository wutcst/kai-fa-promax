package com.guandan.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 客户端发送的出牌消息数据
 * 负责人：成员A（核心引擎与逻辑）
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
}
