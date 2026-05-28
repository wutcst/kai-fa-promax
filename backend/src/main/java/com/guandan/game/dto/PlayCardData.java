package com.guandan.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 客户端发送的出牌消息数据
 * 负责人：成员A（核心引擎与逻辑）
 *
 * <p>玩家在前端选择要出的牌后，通过 WebSocket 发送的消息体。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *   <li>cards - 要出的卡牌ID列表（空列表表示放弃出牌/过牌）</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>cards 为空列表 → 服务端按过牌处理</li>
 *   <li>cards 中包含非法卡牌ID → 服务端校验后拒绝</li>
 *   <li>cards 中的牌玩家手牌中没有 → 服务端校验后拒绝</li>
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
}
