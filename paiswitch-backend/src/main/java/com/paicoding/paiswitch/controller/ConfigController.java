package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.ConfigDto;
import com.paicoding.paiswitch.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Configuration", description = "User configuration management APIs")
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ConfigController {

    private final ConfigService configService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Get current user configuration")
    @GetMapping
    public ApiResponse<ConfigDto.ConfigInfo> getConfig(
            @RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(configService.getUserConfig(userId));
    }

    @Operation(summary = "Update user configuration")
    @PutMapping
    public ApiResponse<ConfigDto.ConfigInfo> updateConfig(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ConfigDto.UpdateRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(configService.updateUserConfig(userId, request));
    }

    @Operation(summary = "Get configuration backups")
    @GetMapping("/backups")
    public ApiResponse<ConfigDto.BackupListResponse> getBackups(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(configService.getBackups(userId, page, size));
    }

    @Operation(summary = "Restore configuration from backup")
    @PostMapping("/backups/{backupId}/restore")
    public ApiResponse<ConfigDto.ConfigInfo> restoreBackup(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long backupId) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(configService.restoreBackup(userId, backupId));
    }

    private Long extractUserId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
