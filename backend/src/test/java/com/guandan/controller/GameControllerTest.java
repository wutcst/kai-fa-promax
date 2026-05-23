package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.entity.Room;
import com.guandan.entity.RoomPlayer;
import com.guandan.entity.User;
import com.guandan.game.dto.RoomStatusResponse;
import com.guandan.mapper.UserMapper;
import com.guandan.service.AuthService;
import com.guandan.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GameController 准备状态可见性回归验证
 *
 * 覆盖场景：
 * - 准备/取消准备接口切换
 * - 房主无需准备逻辑
 * - 等待页房间状态查询
 * - 房主提示信息
 * - 异常场景：房间不存在、玩家不在房间中、Token无效
 */
@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RoomService roomService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private GameController gameController;

    private static final String VALID_TOKEN = "Bearer valid-token";
    private static final Long USER_ID = 1L;
    private static final Long CREATOR_ID = 2L;
    private static final String ROOM_NO = "123456";

    private Room createTestRoom(Long roomId, Integer status, Long creatorId) {
        Room room = new Room();
        room.setId(roomId);
        room.setRoomNo(ROOM_NO);
        room.setStatus(status);
        room.setCreatorId(creatorId);
        return room;
    }

    @BeforeEach
    void setUp() {
        lenient().when(authService.validateToken("valid-token")).thenReturn(USER_ID);
    }

    // ── 准备接口测试 ─────────────────────────────────

    @Test
    void testReady_invalidToken() {
        when(authService.validateToken("invalid")).thenReturn(null);

        Map<String, String> body = new HashMap<>();
        body.put("roomNo", ROOM_NO);

        Result<?> result = gameController.ready("Bearer invalid", body);
        assertTrue(result.getMessage().contains("用户未登录") || result.getMessage().contains("Token已过期"),
                "无效Token应返回未登录错误");
    }

    @Test
    void testReady_roomNotFound() {
        Map<String, String> body = new HashMap<>();
        body.put("roomNo", "999999");
        when(roomService.getRoomByRoomNo("999999")).thenReturn(null);

        Result<?> result = gameController.ready(VALID_TOKEN, body);
        assertEquals("房间不存在：999999", result.getMessage());
    }

    @Test
    void testReady_roomNoEmpty() {
        Map<String, String> body = new HashMap<>();
        body.put("roomNo", "");

        Result<?> result = gameController.ready(VALID_TOKEN, body);
        assertEquals("房间号不能为空", result.getMessage());
    }

    @Test
    void testReady_playerNotInRoom() {
        Map<String, String> body = new HashMap<>();
        body.put("roomNo", ROOM_NO);

        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);
        when(roomService.getRoomPlayer(1L, USER_ID)).thenReturn(null);

        Result<?> result = gameController.ready(VALID_TOKEN, body);
        assertEquals("玩家不在该房间中", result.getMessage());
    }

    @Test
    void testReady_creatorNoNeedToReady() {
        // 房主调用准备接口 — 应返回房主无需准备
        Map<String, String> body = new HashMap<>();
        body.put("roomNo", ROOM_NO);

        when(authService.validateToken("valid-token")).thenReturn(CREATOR_ID);
        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);

        List<RoomPlayer> players = new ArrayList<>();
        RoomPlayer p = new RoomPlayer();
        p.setId(10L);
        p.setUserId(CREATOR_ID);
        p.setIsReady(0);
        players.add(p);

        when(roomService.getRoomPlayers(1L)).thenReturn(players);

        Result<?> result = gameController.ready("Bearer valid-token", body);
        assertTrue(result.isSuccess());
    }

    // ── 等待页房间状态查询测试 ─────────────────────

    @Test
    void testGetRoomStatus_roomNotFound() {
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(null);

        Result<RoomStatusResponse> result = gameController.getRoomStatus(VALID_TOKEN, ROOM_NO);
        assertEquals("房间不存在：" + ROOM_NO, result.getMessage());
    }

    @Test
    void testGetRoomStatus_waitingStatus() {
        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);
        when(roomService.getPlayerCount(1L)).thenReturn(2);

        List<RoomPlayer> players = new ArrayList<>();
        RoomPlayer p1 = new RoomPlayer();
        p1.setUserId(CREATOR_ID);
        p1.setSeatIndex(0);
        p1.setIsReady(1);
        RoomPlayer p2 = new RoomPlayer();
        p2.setUserId(USER_ID);
        p2.setSeatIndex(1);
        p2.setIsReady(0);
        players.add(p1);
        players.add(p2);
        when(roomService.getRoomPlayers(1L)).thenReturn(players);

        Result<RoomStatusResponse> result = gameController.getRoomStatus(VALID_TOKEN, ROOM_NO);
        assertNotNull(result.getData());
        assertEquals("WAITING", result.getData().getStatus());
        assertFalse(result.getData().getAllReady());
    }

    @Test
    void testGetRoomStatus_allReady() {
        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);
        when(roomService.getPlayerCount(1L)).thenReturn(2);

        List<RoomPlayer> players = new ArrayList<>();
        RoomPlayer p1 = new RoomPlayer();
        p1.setUserId(USER_ID);
        p1.setSeatIndex(0);
        p1.setIsReady(1);
        RoomPlayer p2 = new RoomPlayer();
        p2.setUserId(3L);
        p2.setSeatIndex(1);
        p2.setIsReady(1);
        players.add(p1);
        players.add(p2);
        // CREATOR_ID is not an actual player here, so all non-creator players are ready
        when(roomService.getRoomPlayers(1L)).thenReturn(players);

        // Set room creator to a user not in this room
        room.setCreatorId(99L);

        Result<RoomStatusResponse> result = gameController.getRoomStatus(VALID_TOKEN, ROOM_NO);
        assertTrue(result.getData().getAllReady());
    }

    // ── 房主提示测试 ──────────────────────────────

    @Test
    void testGetHostTip_nonCreator() {
        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);
        when(roomService.getPlayerCount(1L)).thenReturn(2);

        List<RoomPlayer> players = new ArrayList<>();
        RoomPlayer p = new RoomPlayer();
        p.setUserId(CREATOR_ID);
        p.setIsReady(1);
        RoomPlayer p2 = new RoomPlayer();
        p2.setUserId(USER_ID);
        p2.setIsReady(0);
        players.add(p);
        players.add(p2);
        when(roomService.getRoomPlayers(1L)).thenReturn(players);

        User u = new User();
        u.setNickname("testUser");
        when(userMapper.selectById(USER_ID)).thenReturn(u);

        Result<Map<String, Object>> result = gameController.getHostTip(VALID_TOKEN, ROOM_NO);
        assertFalse((Boolean) result.getData().get("isCreator"),
                "非房主用户的 isCreator 应为 false");
        assertFalse((Boolean) result.getData().get("showTip"),
                "非房主用户不应显示操作提示");
    }

    @Test
    void testGetHostTip_creatorWithEnoughPlayers() {
        when(authService.validateToken("valid-token")).thenReturn(CREATOR_ID);
        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);
        when(roomService.getPlayerCount(1L)).thenReturn(4);

        List<RoomPlayer> players = new ArrayList<>();
        RoomPlayer p1 = new RoomPlayer();
        p1.setUserId(CREATOR_ID);
        p1.setIsReady(1);
        RoomPlayer p2 = new RoomPlayer();
        p2.setUserId(3L);
        p2.setIsReady(1);
        RoomPlayer p3 = new RoomPlayer();
        p3.setUserId(4L);
        p3.setIsReady(1);
        RoomPlayer p4 = new RoomPlayer();
        p4.setUserId(5L);
        p4.setIsReady(1);
        players.add(p1);
        players.add(p2);
        players.add(p3);
        players.add(p4);
        when(roomService.getRoomPlayers(1L)).thenReturn(players);

        Result<Map<String, Object>> result = gameController.getHostTip("Bearer valid-token", ROOM_NO);
        assertTrue((Boolean) result.getData().get("isCreator"));
        assertTrue((Boolean) result.getData().get("canStart"),
                "房主且有足够已准备玩家时应可开始");
    }

    @Test
    void testGetHostTip_roomNotFound() {
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(null);
        Result<Map<String, Object>> result = gameController.getHostTip(VALID_TOKEN, ROOM_NO);
        assertTrue(result.getMessage().contains("房间不存在"));
    }

    // ── 等待页完整状态测试 ─────────────────────────

    @Test
    void testGetWaitingStatus_playersList() {
        Room room = createTestRoom(1L, 0, CREATOR_ID);
        when(roomService.getRoomByRoomNo(ROOM_NO)).thenReturn(room);
        when(roomService.getPlayerCount(1L)).thenReturn(2);

        List<RoomPlayer> players = new ArrayList<>();
        RoomPlayer p1 = new RoomPlayer();
        p1.setUserId(CREATOR_ID);
        p1.setSeatIndex(0);
        p1.setIsReady(1);
        RoomPlayer p2 = new RoomPlayer();
        p2.setUserId(USER_ID);
        p2.setSeatIndex(1);
        p2.setIsReady(0);
        players.add(p1);
        players.add(p2);
        when(roomService.getRoomPlayers(1L)).thenReturn(players);

        User u = new User();
        u.setUsername("player1");
        when(userMapper.selectById(USER_ID)).thenReturn(u);
        User creator = new User();
        creator.setUsername("creator");
        when(userMapper.selectById(CREATOR_ID)).thenReturn(creator);

        Result<Map<String, Object>> result = gameController.getWaitingStatus(VALID_TOKEN, ROOM_NO);
        assertNotNull(result.getData());
        assertEquals(ROOM_NO, result.getData().get("roomNo"));
        assertFalse((Boolean) result.getData().get("allReady"),
                "有玩家未准备时 allReady 应为 false");
    }
}
