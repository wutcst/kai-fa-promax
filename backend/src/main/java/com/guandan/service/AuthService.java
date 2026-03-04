package com.guandan.service;

import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.entity.User;
import com.guandan.mapper.UserMapper;
import com.guandan.util.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public RegisterResponse register(RegisterRequest request) {
        // 用户名去空格再查重
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

        // TODO: Replace placeholder token with JWT in Issue 1-C
        String token = "placeholder-token-" + user.getId();

        return new RegisterResponse(user.getId(), user.getUsername(), user.getNickname(), token);
    }

    // TODO: login() method will be added when LoginRequest DTO is available (Issue 1-C)
}
