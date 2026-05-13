package com.guandan.service;

import com.guandan.dto.LoginResponse;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.dto.UserInfoResponse;
import com.guandan.entity.User;
import com.guandan.util.PasswordUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 认证服务类
 * 处理用户注册和登录的核心业务逻辑
 */
@Service
public class AuthService {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     * 校验用户名唯一性，加密密码，保存用户信息，返回Token
     *
     * @param request 注册请求
     * @return 注册响应（含用户信息 + Token）
     */
    public RegisterResponse register(RegisterRequest request) {
        // 参数空值校验
        if (request == null) {
            throw new RuntimeException("注册请求参数不能为空");
        }
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) {
            throw new RuntimeException("昵称不能为空");
        }

        // 校验用户名是否已存在（重复提交保护）
        User exists = userService.findByUsername(request.getUsername().trim());
        if (exists != null) {
            throw new RuntimeException("该账号已被注册");
        }

        // 密码长度二次校验
        String rawPassword = request.getPassword().trim();
        if (rawPassword.length() < 6 || rawPassword.length() > 10) {
            throw new RuntimeException("密码长度必须在6-10位之间");
        }

        // 加密密码
        String hashedPassword = PasswordUtil.hashPassword(rawPassword);

        // 保存用户信息
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(hashedPassword);
        user.setNickname(request.getNickname().trim());
        user.setAvatar(request.getAvatar());
        user.setPhone(request.getPhone());
        user.setOnline(1);

        boolean saved = userService.saveUser(user);
        if (!saved) {
            throw new RuntimeException("注册失败，请稍后重试");
        }

        // 生成Token
        if (user.getId() == null) {
            throw new RuntimeException("用户ID获取失败，注册异常");
        }
        String token = "temp_token_" + user.getId();

        // 返回注册结果
        RegisterResponse response = new RegisterResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setToken(token);
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
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
        // 参数空值校验
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }

        // 查询用户信息
        User user = userService.findByUsername(username.trim());
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }

        // 校验账号状态
        if (user.getOnline() != null && user.getOnline() == 1) {
            throw new RuntimeException("该账号已登录，请先退出");
        }

        // 校验密码
        if (!PasswordUtil.checkPassword(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 更新在线状态
        user.setOnline(1);
        boolean updated = userService.updateUser(user);
        if (!updated) {
            throw new RuntimeException("登录状态更新失败");
        }

        // 生成Token
        Long userId = user.getId();
        if (userId == null) {
            throw new RuntimeException("用户ID异常");
        }
        String token = "temp_token_" + userId;

        // 返回登录结果
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(userId);
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
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
        User user = userService.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setPhone(user.getPhone());
        return response;
    }

    /**
     * 验证Token有效性
     *
     * @param token 认证Token
     * @return 用户ID
     */
    public Long validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }

        if (!token.startsWith("temp_token_")) {
            throw new RuntimeException("Token格式无效");
        }

        String userIdStr = token.substring("temp_token_".length());
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Token解析失败");
        }
    }
}
