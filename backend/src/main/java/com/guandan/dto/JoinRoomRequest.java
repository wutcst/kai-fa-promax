package com.guandan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 加入房间请求
 *
 * 包含房间号校验逻辑，确保6位数字格式。
 * 职责边界：仅做请求参数封装和校验，不包含业务逻辑。
 * 重构说明：移除冗余的校验方法，保留 validateRequest 作为统一校验入口，
 * 提取公共校验常量，使代码职责更清晰。
 */
@Data
public class JoinRoomRequest {

    /** 房间号正则：6位数字 */
    public static final String ROOM_NO_REGEX = "^\\d{6}$";

    /** 用户ID（由服务端从Token解析） */
    private Long userId;

    /** 房间号（6位数字） */
    @NotBlank(message = "房间号不能为空")
    @Pattern(regexp = ROOM_NO_REGEX, message = "房间号必须是6位数字")
    private String roomNo;

    /** 房间密码（预留，加锁房间需要） */
    private String password;

    /** 校验房间号是否有效 */
    public boolean isValid() {
        return roomNo != null && roomNo.matches(ROOM_NO_REGEX);
    }

    /** 校验房间号是否为空 */
    public boolean hasRoomNo() {
        return roomNo != null && !roomNo.trim().isEmpty();
    }

    /** 获取处理后的房间号（去除前后空格） */
    public String getTrimmedRoomNo() {
        return roomNo != null ? roomNo.trim() : null;
    }

    /** 校验请求参数完整性：返回错误信息字符串，无错误返回null */
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
