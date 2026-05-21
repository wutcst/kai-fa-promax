package com.guandan.game.dto;

import com.guandan.entity.RoomPlayer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 房间状态响应
 *
 * 用于返回房间当前的状态信息，包括：
 * - 房间ID
 * - 房间状态（等待中、游戏中、已结束）
 * - 当前玩家数量
 * - 最大玩家数量
 * - 提示信息
 * - 玩家列表及准备状态
 * - 房主操作提示
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatusResponse {
    /** 房间ID */
    private String roomId;

    /** 房间号 */
    private String roomNo;

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

    /** 玩家列表及状态 */
    private List<Map<String, Object>> players;

    /** 房主提示信息 */
    private String hostTip;

    /** 房主是否可开始游戏 */
    private Boolean canStart;

    /**
     * 构建标准状态响应
     */
    public static RoomStatusResponse buildWaitingResponse(String roomNo, Integer status,
                                                          int playerCount, int maxPlayers,
                                                          Integer seatIndex, boolean isCreator,
                                                          boolean allReady, String message) {
        RoomStatusResponse response = new RoomStatusResponse();
        response.setRoomId("room_" + roomNo);
        response.setRoomNo(roomNo);
        response.setStatus(status == 0 ? "WAITING" : status == 1 ? "PLAYING" : "FINISHED");
        response.setPlayerCount(playerCount);
        response.setMaxPlayers(maxPlayers);
        response.setMessage(message);
        response.setSeatIndex(seatIndex);
        response.setIsCreator(isCreator);
        response.setAllReady(allReady);
        return response;
    }

    /**
     * 构建满员状态响应
     */
    public static RoomStatusResponse buildFullResponse(String roomNo, int playerCount,
                                                       boolean isCreator, Integer seatIndex) {
        return buildWaitingResponse(roomNo, 0, playerCount, 4,
                seatIndex, isCreator, false, "房间已满，等待房主开始游戏");
    }

    /**
     * 构建游戏中状态响应
     */
    public static RoomStatusResponse buildPlayingResponse(String roomNo, int playerCount,
                                                          boolean isCreator, Integer seatIndex) {
        return buildWaitingResponse(roomNo, 1, playerCount, 4,
                seatIndex, isCreator, true, "游戏进行中");
    }

    /**
     * 构建已结束状态响应
     */
    public static RoomStatusResponse buildFinishedResponse(String roomNo, int playerCount,
                                                           boolean isCreator, Integer seatIndex) {
        return buildWaitingResponse(roomNo, 2, playerCount, 4,
                seatIndex, isCreator, false, "游戏已结束");
    }
}
