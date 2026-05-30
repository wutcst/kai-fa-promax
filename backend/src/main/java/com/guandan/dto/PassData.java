package com.guandan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 玩家放弃出牌广播消息的数据部分
 *
 * <p>当玩家选择不出牌（过牌）时，后端广播给所有客户端的事件负载。
 *
 * <p>职责边界：
 * <ul>
 *   <li>playerId — 过牌玩家ID，不可为空</li>
 *   <li>currentPlayerId — 过牌后的下一个回合玩家</li>
 * </ul>
 *
 * <p><b>过牌流程说明：</b>
 * <ul>
 *   <li>玩家选择过牌时，服务端广播 PassData 给房间内所有玩家</li>
 *   <li>currentPlayerId 为过牌后的下一个出牌玩家</li>
 *   <li>若连续过牌导致清桌，则通过 TABLE_CLEAR 事件通知客户端</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>playerId 为空（null/空字符串）— hasValidPlayer() 返回 false</li>
 *   <li>currentPlayerId 为空 — hasCompleteTurnInfo() 返回 false</li>
 *   <li>后端校验不通过时返回错误消息，不广播 PassData</li>
 * </ul>
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
     * 校验数据完整性
     * @return true 如果 playerId 不为空
     */
    public boolean hasValidPlayer() {
        return playerId != null && !playerId.trim().isEmpty();
    }

    /**
     * 检查是否包含完整的回合信息
     * @return true 如果 playerId 和 currentPlayerId 都不为空
     */
    public boolean hasCompleteTurnInfo() {
        return hasValidPlayer() && currentPlayerId != null && !currentPlayerId.trim().isEmpty();
    }
}
