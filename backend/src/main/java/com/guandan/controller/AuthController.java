package com.guandan.controller;

import com.guandan.annotation.IgnoreAuth;
import com.guandan.common.Result;
import com.guandan.dto.LoginRequest;
import com.guandan.dto.LoginResponse;
import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.dto.UserInfoResponse;
import com.guandan.service.AuthService;
import com.guandan.service.UserService;
import com.guandan.util.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @IgnoreAuth
    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @IgnoreAuth
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/user/info")
    public Result<UserInfoResponse> userInfo() {
        return Result.ok(userService.currentUserInfo(UserContext.getUserId()));
    }

    @GetMapping("/user/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        return Result.ok(authService.isUsernameAvailable(username));
    }
}
