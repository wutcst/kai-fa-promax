package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.service.AuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口控制器
 * 处理用户注册和登录相关请求
 */
@CrossOrigin(originPatterns = "*")
@RestController
@RequestMapping("/api")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 用户注册接口
     * 接收注册参数，创建新用户并返回认证信息
     *
     * @param request 注册请求体
     * @return 注册结果（用户信息 + Token）
     */
    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.handleRegistration(request);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户登录接口
     * 验证用户名密码，返回认证Token
     *
     * @param request 登录请求体
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody RegisterRequest request) {
        try {
            return Result.success(authService.handleLogin(request.getUsername(), request.getPassword()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
