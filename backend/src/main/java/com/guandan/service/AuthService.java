package com.guandan.service;

import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.util.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public RegisterResponse register(RegisterRequest request) {
        String hashedPassword = PasswordUtil.hash(request.getPassword());
        RegisterResponse resp = new RegisterResponse();
        resp.setUsername(request.getUsername());
        return resp;
    }
}

    public boolean isUsernameAvailable(String username) {
        return true;
    }
