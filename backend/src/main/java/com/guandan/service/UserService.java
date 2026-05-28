package com.guandan.service;

import com.guandan.dto.UserInfoResponse;
import com.guandan.entity.User;
import com.guandan.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserInfoResponse currentUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserInfoResponse resp = new UserInfoResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setAvatar(user.getAvatarUrl());
        resp.setPhone(user.getPhone());
        return resp;
    }
}
