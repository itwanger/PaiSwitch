package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.SwitchDto;
import com.paicoding.paiswitch.service.SwitchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Model Switching", description = "AI model switching APIs")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SwitchController {

    private final SwitchService switchService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Switch to a specific provider")
    @PostMapping("/switch")
    public ApiResponse<SwitchDto.SwitchResult> switchProvider(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SwitchDto.SwitchRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(switchService.switchToProvider(userId, request));
    }

    private Long extractUserId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
