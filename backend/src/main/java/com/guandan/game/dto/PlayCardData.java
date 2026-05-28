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
}
