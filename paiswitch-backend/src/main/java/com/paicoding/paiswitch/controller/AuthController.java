package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.domain.dto.AuthDto;
import com.paicoding.paiswitch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "User authentication APIs")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ApiResponse<AuthDto.LoginResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @Operation(summary = "Login")
    @PostMapping("/login")
    public ApiResponse<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
