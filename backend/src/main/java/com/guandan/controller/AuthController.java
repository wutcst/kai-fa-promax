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
     *
     * 接收新用户注册信息，完成账号创建并返回认证Token。
     *
     * 请求参数（application/json）：
     * - username: String, 必填, 6位纯数字
     * - password: String, 必填, 6-10位字母数字
     * - nickname: String, 必填, 最多10位（字母/数字/汉字）
     * - avatar: String, 可选, Base64 SVG
     * - phone: String, 可选, 手机号
     *
     * 返回结构：
     * - code: int, 200=成功, 500=失败
     * - message: String, 提示信息
     * - data: RegisterResponse {userId, username, token, nickname, avatar}
     *
     * 异常场景：
     * - 400: 参数校验不通过
     * - 500: 该账号已被注册 / 注册失败，请稍后重试
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
     *
     * 验证用户身份并返回认证Token。
     *
     * 请求参数（application/json）：
     * - username: String, 必填, 6位数字账号
     * - password: String, 必填, 登录密码
     *
     * 返回结构：
     * - code: int, 200=成功, 500=失败
     * - message: String, 提示信息
     * - data: LoginResponse {token, userId, username, nickname, avatar}
     *
     * 异常场景：
     * - 400: 参数校验不通过
     * - 500: 账号不存在 / 密码错误 / 该账号已登录
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
