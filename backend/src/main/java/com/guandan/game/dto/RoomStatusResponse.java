package com.guandan.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房间状态响应
 *
 * 用于返回房间当前的状态信息，包括：
 * - 房间ID
 * - 房间状态（等待中、游戏中、已结束）
 * - 当前玩家数量
 * - 最大玩家数量
 * - 提示信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatusResponse {
    /** 房间ID */
    private String roomId;

    /** 房间状态：WAITING（等待中）、PLAYING（游戏中）、FINISHED（已结束） */
    private String status;

    /** 当前玩家数量 */
    private int playerCount;

    /** 最大玩家数量（固定为4） */
    private int maxPlayers;

    /** 提示信息，如等待人数不足、游戏即将开始等 */
    private String message;

    /** 座位号（0-3） */
    private Integer seatIndex;

    /** 是否为房主 */
    private Boolean isCreator;

    /** 是否全部准备就绪 */
    private Boolean allReady;
}
