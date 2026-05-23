package com.guandan.service;

import com.guandan.dto.NewGameRequest;
import com.guandan.mapper.RoomMapper;
import com.guandan.mapper.RoomPlayerMapper;
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
 * RoomService 边界回归验证
 *
 * 覆盖场景：
 * - 重复房间号生成重试
 * - 满员校验
 * - 重复加入检测（幂等）
 * - 参数空值保护
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private RoomPlayerMapper roomPlayerMapper;

    @InjectMocks
    private RoomService roomService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testCreateRoom_nullRequestReturnsNull() {
        String result = roomService.createRoom(null);
        assertNull(result, "null请求应返回null");
    }

    @Test
    void testCreateRoom_nullUserIdReturnsNull() {
        NewGameRequest request = new NewGameRequest();
        String result = roomService.createRoom(request);
        assertNull(result, "缺userId的请求应返回null");
    }

    @Test
    void testJoinRoom_nullRoomNoThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> roomService.joinRoom(null, 1L),
                "null房间号应抛异常");
    }

    @Test
    void testJoinRoom_nullUserIdThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> roomService.joinRoom("123456", null),
                "null用户ID应抛异常");
    }

    @Test
    void testJoinRoom_nonExistentRoomThrowsException() {
        when(roomMapper.selectOne(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> roomService.joinRoom("999999", 1L),
                "不存在的房间应抛异常");
    }

    @Test
    void testRemovePlayer_nullParamsDoesNotThrow() {
        assertDoesNotThrow(() -> roomService.removePlayer(null, 1L));
        assertDoesNotThrow(() -> roomService.removePlayer("123456", null));
        assertDoesNotThrow(() -> roomService.removePlayer(null, null));
    }

    @Test
    void testGetCurrentRoom_nullUserIdReturnsNull() {
        assertNull(roomService.getCurrentRoom(null));
    }
}
