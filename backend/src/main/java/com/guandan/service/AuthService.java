package com.guandan.service;

import com.guandan.dto.LoginResponse;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.dto.UserInfoResponse;
import com.guandan.entity.User;
import com.guandan.util.PasswordUtil;
import org.springframework.stereotype.Service;

/**
 * 认证服务类
 * 处理用户注册和登录的核心业务逻辑
 */
@Service
public class AuthService {

    /**
     * 用户注册
     * 校验用户名唯一性，加密密码，保存用户信息，返回Token
     *
     * @param request 注册请求
     * @return 注册响应（含用户信息 + Token）
     */
    public RegisterResponse register(RegisterRequest request) {
        // 校验用户名是否已存在
        // 加密密码
        String hashedPassword = PasswordUtil.hashPassword(request.getPassword());

        // 保存用户信息
        // 生成Token
        // 返回注册结果

        RegisterResponse response = new RegisterResponse();
        // TODO: 补充完整实现
        return response;
    }

    /**
     * 用户登录
     * 验证用户名密码，生成Token，返回用户信息
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录响应（含用户信息 + Token）
     */
    public LoginResponse login(String username, String password) {
        // 查询用户信息
        // 校验密码
        // 生成Token
        // 返回登录结果

        LoginResponse response = new LoginResponse();
        // TODO: 补充完整实现
        return response;
    }

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfoResponse getUserInfo(Long userId) {
        // 查询用户详情
        UserInfoResponse response = new UserInfoResponse();
        // TODO: 补充完整实现
        return response;
    }

    /**
     * 验证Token有效性
     *
     * @param token 认证Token
     * @return 用户ID
     */
    public Long validateToken(String token) {
        // 解析Token
        // 验证有效性
        // 返回用户ID
        return null;
    }
}
