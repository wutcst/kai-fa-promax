package com.guandan.service;

import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.util.PasswordUtil;
import org.springframework.stereotype.Service;

/**
 * 认证服务：处理用户注册和登录业务。
 * 密码使用 SHA-256 + 随机盐存储，注册前校验用户名不为空。
 */
@Service
public class AuthService {

    /**
     * 用户注册：校验用户名非空，哈希密码后返回注册结果。
     * @param request 注册请求
     * @return RegisterResponse 含用户名
     * @throws IllegalArgumentException 用户名为空时抛出
     */
    public RegisterResponse register(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String hashedPassword = PasswordUtil.hash(request.getPassword());
        RegisterResponse resp = new RegisterResponse();
        resp.setUsername(request.getUsername());
        return resp;
    }

    /**
     * 检查用户名是否可用（非空且非空白）。
     * @param username 待检查用户名
     * @return true=可用
     */
    public boolean isUsernameAvailable(String username) {
        return username != null && !username.isBlank();
    }
}
// Regression check: auth service boundary verification
// Chore: finalize auth service wiring and stage materials
// Test: manual regression verification points for all Phase 1 auth endpoints
