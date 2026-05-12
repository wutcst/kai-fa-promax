package com.guandan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册响应
 * 返回注册成功后生成的用户信息和Token
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    /** 用户ID */
    private Long userId;

    /** 用户名（6位数字账号） */
    private String username;

    /** 认证Token */
    private String token;

    /** 用户昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;
}
