package com.guandan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 玩家放弃出牌广播消息的数据部分
 *
 * <p>当玩家选择不出牌（过牌）时，后端广播给所有客户端的事件负载。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassData {
    /**
     * 放弃出牌的玩家ID
     */
    private String playerId;

    /**
     * 当前轮到出牌的玩家ID（放弃后的下一个玩家）
     */
    private String currentPlayerId;

    /**
     * 检查数据是否有效
     * @return true 如果 playerId 不为空
     */
    public boolean isValid() {
        return playerId != null && !playerId.trim().isEmpty();
    }
}
