package com.guandan.service;

import com.guandan.dto.LoginRequest;
import com.guandan.dto.LoginResponse;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.entity.User;
import com.guandan.mapper.UserMapper;
import com.guandan.util.JwtUtil;
import com.guandan.util.PasswordUtil;
import org.springframework.stereotype.Service;

/**
 * 认证服务 — 处理用户注册和登录业务逻辑。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final TokenCache tokenCache;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil, TokenCache tokenCache) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.tokenCache = tokenCache;
    }

    public RegisterResponse register(RegisterRequest request) {
        String normalizedUsername = request.getUsername().trim();
        User existing = userMapper.findByUsername(normalizedUsername);
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setAvatarUrl(request.getAvatar());
        user.setPhone(request.getPhone());
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        tokenCache.cacheToken(user.getId(), token);

        return new RegisterResponse(user.getId(), user.getUsername(), user.getNickname(), token);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername().trim());
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        tokenCache.cacheToken(user.getId(), token);

        return new LoginResponse(user.getId(), user.getUsername(), user.getNickname(), token);
    }

    public boolean isUsernameAvailable(String username) {
        return userMapper.findByUsername(username.trim()) == null;
    }

    public void logout(Long userId) {
        tokenCache.removeToken(userId);
    }
}
