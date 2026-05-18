/**
 * 用户注册接口 (POST /api/register)
 *
 * 测试验证点：
 * 1. 正常注册流程 - 返回userId、username、token、nickname
 * 2. 重复用户名 - 返回"该账号已被注册"
 * 3. 空参数请求 - 返回参数校验错误
 * 4. 密码格式非法 - 返回校验提示
 *
 * 用户登录接口 (POST /api/login)
 *
 * 测试验证点：
 * 1. 正常登录流程 - 返回token和用户信息
 * 2. 账号不存在 - 返回"账号不存在"
 * 3. 密码错误 - 返回"密码错误"
 * 4. 空参数请求 - 返回参数校验错误
 *
 * 密码加密工具 (PasswordUtil)
 *
 * 测试验证点：
 * 1. hashPassword返回密文非空且不等同明文
 * 2. checkPassword对正确密码返回true
 * 3. checkPassword对错误密码返回false
 */

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
 *
 * 处理用户注册和登录相关请求，提供RESTful API入口。
 * 所有接口返回统一Result格式：{code, message, data}
 */
@CrossOrigin(originPatterns = "*")
@RestController
@RequestMapping("/api")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 用户注册接口 (POST /api/register)
     *
     * 请求体：{username(6位数字), password(6-10位), nickname(最多10位), avatar(可选), phone(可选)}
     * 成功返回：{code:200, data:{userId, username, token, nickname, avatar}}
     * 失败返回：{code:500, message:"错误描述"}
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
     * 用户登录接口 (POST /api/login)
     *
     * 请求体：{username(6位数字), password}
     * 成功返回：{code:200, data:{token, userId, username, nickname, avatar}}
     * 失败返回：{code:500, message:"账号不存在/密码错误"}
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

    // ── Phase 1 配置说明 ──
    // 1. 登录：POST /api/login → AuthService.handleLogin()
    // 2. 注册：POST /api/register → AuthService.handleRegistration()
    // 3. 退出：POST /api/user/logout → 清除服务端 Token 缓存
    // 4. 信息：GET /api/user/info → AuthService.getUserInfo()
    // 5. Token：JWT，24h 过期，Authorization: Bearer
    // 6. 密码：BCrypt 加密（强度 10）
    // 7. 跨域：@CrossOrigin(originPatterns = "*")
    // 8. 校验：@Valid + 全局异常处理
    // 9. 响应格式：统一 Result<T> 包装
