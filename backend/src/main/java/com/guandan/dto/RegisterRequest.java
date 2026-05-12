package com.guandan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数
 * 包含用户名、密码、昵称等注册必需信息
 */
@Data
public class RegisterRequest {

    /** 用户名（系统分配的6位纯数字账号） */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "账号必须是6位纯数字")
    private String username;

    /** 登录密码（6-10位字母数字组合） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 10, message = "密码长度必须在6-10位之间")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "密码只能包含字母和数字")
    private String password;

    /** 用户昵称（最多10位，支持字母、数字、汉字） */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 10, message = "昵称长度不能超过10位")
    @Pattern(regexp = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$", message = "昵称只能包含字母、数字、汉字")
    private String nickname;

    /** 头像（Base64编码的SVG图片） */
    private String avatar;

    /** 手机号（可选字段） */
    private String phone;
}
