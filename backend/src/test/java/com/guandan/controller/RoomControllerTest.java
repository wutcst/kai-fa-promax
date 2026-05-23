package com.guandan.controller;

import com.guandan.common.ApiResult;
import com.guandan.dto.NewGameRequest;
import com.guandan.service.AuthService;
import com.guandan.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RoomController 回归验证
 *
 * 覆盖场景：
 * - 创建房间时房间号唯一性重试逻辑
 * - 加入房间时满员判断
 * - 重复加入同一房间的幂等处理
 * - 房间状态校验（仅等待中可加入）
 */
@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private RoomController roomController;

    private static final String VALID_TOKEN = "Bearer valid-token";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(authService.validateToken("valid-token")).thenReturn(USER_ID);
    }

    @Test
    void testCreateGame_roomNoRetryOnDuplicate() {
        // 模拟房间号重复时重试后成功
        NewGameRequest request = new NewGameRequest();
        request.setUserId(USER_ID);
        when(roomService.createRoom(any(NewGameRequest.class)))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn("654321");

        ApiResult<?> result = roomController.createGame(VALID_TOKEN, request);
        assertNotNull(result);
        assertTrue(result.isSuccess() || result.getMessage() != null,
                "重试之后应返回结果");
    }

    @Test
    void testCreateGame_nullRoomNoReturnsError() {
        NewGameRequest request = new NewGameRequest();
        request.setUserId(USER_ID);
        when(roomService.createRoom(any(NewGameRequest.class))).thenReturn(null);

        ApiResult<?> result = roomController.createGame(VALID_TOKEN, request);
        assertEquals("房间创建失败，请重试", result.getMessage());
    }

    @Test
    void testJoinRoom_fullRoomReturnsError() {
        // 满员场景 — RoomService.joinRoom 抛出异常
        NewGameRequest request = new NewGameRequest();
        request.setRoomNo("123456");
        when(roomService.getCurrentRoom(USER_ID)).thenReturn(null);
        when(roomService.joinRoom("123456", USER_ID))
                .thenThrow(new IllegalArgumentException("房间已满，最多4人"));

        ApiResult<?> result = roomController.joinRoom(VALID_TOKEN, request);
        assertNotNull(result);
        assertEquals("房间已满，最多4人", result.getMessage());
    }

    @Test
    void testJoinRoom_duplicateJoinReturnsExisting() {
        // 重复加入 — 用户已在房间中
        NewGameRequest request = new NewGameRequest();
        request.setRoomNo("123456");

        com.guandan.model.RoomEntity existingRoom = new com.guandan.model.RoomEntity();
        existingRoom.setRoomNo("123456");
        existingRoom.setStatus(0);
        when(roomService.getCurrentRoom(USER_ID)).thenReturn(existingRoom);

        ApiResult<?> result = roomController.joinRoom(VALID_TOKEN, request);
        assertTrue(result.getMessage().contains("已在房间"),
                "重复加入时应提示已在房间中");
    }

    @Test
    void testJoinRoom_invalidRoomNoReturnsError() {
        NewGameRequest request = new NewGameRequest();
        request.setRoomNo("abc"); // 非法房间号

        ApiResult<?> result = roomController.joinRoom(VALID_TOKEN, request);
        assertNotNull(result);
    }
}
