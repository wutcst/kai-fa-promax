package com.guandan.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JoinRoomRequest 校验逻辑回归验证
 *
 * 覆盖场景：
 * - 空房间号校验
 * - 房间号格式校验（6位数字）
 * - 房间号前后空格处理
 */
class JoinRoomRequestTest {

    @Test
    void testValidateRequest_nullRoomNo() {
        JoinRoomRequest request = new JoinRoomRequest();
        String error = request.validateRequest();
        assertEquals("房间号不能为空", error);
    }

    @Test
    void testValidateRequest_emptyRoomNo() {
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomNo("");
        String error = request.validateRequest();
        assertEquals("房间号不能为空", error);
    }

    @Test
    void testValidateRequest_invalidFormat() {
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomNo("12345");
        String error = request.validateRequest();
        assertEquals("房间号必须是6位数字", error);
    }

    @Test
    void testValidateRequest_roomNoWithSpaces() {
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomNo("  654321  ");
        assertEquals("654321", request.getTrimmedRoomNo());
        assertTrue(request.isValid()); // 去除空格后应通过校验
    }

    @Test
    void testValidateRequest_validRoomNo() {
        JoinRoomRequest request = new JoinRoomRequest();
        request.setRoomNo("654321");
        assertTrue(request.isValid());
        assertTrue(request.hasRoomNo());
        assertNull(request.validateRequest());
    }
}
