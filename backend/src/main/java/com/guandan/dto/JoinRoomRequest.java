package com.guandan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 加入房间请求
 *
 * 包含房间号校验逻辑，确保6位数字格式。
 */
@Data
public class JoinRoomRequest {

    /** 用户ID（由服务端从Token解析） */
    private Long userId;

    /** 房间号（6位数字） */
    @NotBlank(message = "房间号不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "房间号必须是6位数字")
    private String roomNo;

    /** 房间密码（预留，加锁房间需要） */
    private String password;

    /** 校验房间号是否有效 */
    public boolean isValid() {
        return roomNo != null && roomNo.matches("^\\d{6}$");
    }
}
