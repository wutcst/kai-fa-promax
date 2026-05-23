package com.guandan.game.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RoomStatusResponse 状态构建回归验证
 *
 * 覆盖场景：
 * - 等待中/游戏中/已结束状态构建
 * - 满员状态构建
 * - 各工厂方法返回结构一致性
 */
class RoomStatusResponseTest {

    private static final String ROOM_NO = "123456";

    @Test
    void testBuildWaitingResponse() {
        RoomStatusResponse response = RoomStatusResponse.buildWaitingResponse(
                ROOM_NO, 0, 2, 4, 0, true, false, "等待其他玩家加入...");

        assertEquals("room_" + ROOM_NO, response.getRoomId());
        assertEquals(ROOM_NO, response.getRoomNo());
        assertEquals("WAITING", response.getStatus());
        assertEquals(2, response.getPlayerCount());
        assertEquals(4, response.getMaxPlayers());
        assertEquals(0, response.getSeatIndex().intValue());
        assertTrue(response.getIsCreator());
        assertFalse(response.getAllReady());
        assertEquals("等待其他玩家加入...", response.getMessage());
    }

    @Test
    void testBuildFullResponse() {
        RoomStatusResponse response = RoomStatusResponse.buildFullResponse(
                ROOM_NO, 4, true, 0);

        assertEquals("WAITING", response.getStatus());
        assertEquals(4, response.getPlayerCount());
        assertEquals("房间已满，等待房主开始游戏", response.getMessage());
    }

    @Test
    void testBuildPlayingResponse() {
        RoomStatusResponse response = RoomStatusResponse.buildPlayingResponse(
                ROOM_NO, 4, true, 0);

        assertEquals("PLAYING", response.getStatus());
        assertTrue(response.getAllReady());
        assertEquals("游戏进行中", response.getMessage());
    }

    @Test
    void testBuildFinishedResponse() {
        RoomStatusResponse response = RoomStatusResponse.buildFinishedResponse(
                ROOM_NO, 4, false, null);

        assertEquals("FINISHED", response.getStatus());
        assertFalse(response.getIsCreator());
        assertNull(response.getSeatIndex());
        assertEquals("游戏已结束", response.getMessage());
    }

    @Test
    void testStatusMappingWithNullStatus() {
        RoomStatusResponse response = RoomStatusResponse.buildWaitingResponse(
                ROOM_NO, null, 0, 4, null, false, false, "");

        assertEquals("WAITING", response.getStatus());
    }
}
