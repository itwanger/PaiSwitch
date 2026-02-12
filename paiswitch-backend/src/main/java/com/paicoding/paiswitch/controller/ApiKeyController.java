package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.ApiKeyDto;
import com.paicoding.paiswitch.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "API Keys", description = "API key management APIs")
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Set API key for a provider")
    @PostMapping
    public ApiResponse<ApiKeyDto.KeyInfo> setApiKey(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ApiKeyDto.SetKeyRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(apiKeyService.setApiKey(userId, request));
    }

    @Operation(summary = "Get all API keys for current user")
    @GetMapping
    public ApiResponse<List<ApiKeyDto.KeyInfo>> getMyApiKeys(
            @RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(apiKeyService.getUserApiKeys(userId));
    }

    @Operation(summary = "Delete API key for a provider")
    @DeleteMapping("/{providerCode}")
    public ApiResponse<Void> deleteApiKey(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String providerCode) {
        Long userId = extractUserId(authorization);
        apiKeyService.deleteApiKey(userId, providerCode);
        return ApiResponse.success("API key deleted", null);
    }

    private Long extractUserId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
