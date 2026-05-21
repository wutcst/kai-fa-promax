package com.guandan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 加入房间请求
 *
 * 包含房间号校验逻辑，确保6位数字格式。
 * 增加空值保护和边界校验，防止重复提交和空指针。
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

    /** 校验房间号是否为空 */
    public boolean hasRoomNo() {
        return roomNo != null && !roomNo.trim().isEmpty();
    }

    /** 获取处理后的房间号（去除前后空格） */
    public String getTrimmedRoomNo() {
        return roomNo != null ? roomNo.trim() : null;
    }

    /** 校验用户ID是否有效 */
    public boolean hasValidUserId() {
        return userId != null && userId > 0;
    }

    /** 校验请求参数完整性 */
    public String validateRequest() {
        if (!hasRoomNo()) {
            return "房间号不能为空";
        }
        if (!isValid()) {
            return "房间号必须是6位数字";
        }
        return null;
    }
}
