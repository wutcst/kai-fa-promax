package com.guandan.controller;

import com.guandan.dto.RegisterRequest;
import com.guandan.dto.RegisterResponse;
import com.guandan.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
// Regression check: controller endpoint validation
// Chore: consolidated auth module configuration for Phase 1 delivery
// Chore: Phase 1 backend API consolidation - all auth endpoints configured
