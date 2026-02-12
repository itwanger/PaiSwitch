package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.ProviderDto;
import com.paicoding.paiswitch.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Model Providers", description = "AI model provider management APIs")
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Get all active providers (public)")
    @GetMapping
    public ApiResponse<List<ProviderDto.ProviderInfo>> getAllProviders() {
        return ApiResponse.success(providerService.getAllProviders());
    }

    @Operation(summary = "Get providers for current user (with API key status)")
    @GetMapping("/my")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<ProviderDto.ProviderInfo>> getMyProviders(
            @RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(providerService.getProvidersForUser(userId));
    }

    @Operation(summary = "Get provider by code")
    @GetMapping("/{code}")
    public ApiResponse<ProviderDto.ProviderInfo> getProvider(@PathVariable String code) {
        return ApiResponse.success(providerService.getProviderByCode(code));
    }

    @Operation(summary = "Create custom provider")
    @PostMapping("/custom")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ProviderDto.ProviderInfo> createCustomProvider(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ProviderDto.CreateRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(providerService.createCustomProvider(userId, request));
    }

    @Operation(summary = "Update custom provider")
    @PutMapping("/{code}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ProviderDto.ProviderInfo> updateProvider(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String code,
            @RequestBody ProviderDto.UpdateRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(providerService.updateProvider(userId, code, request));
    }

    @Operation(summary = "Update provider configuration (baseUrl, modelName, modelNameSmall)")
    @PutMapping("/{code}/config")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ProviderDto.ProviderInfo> updateProviderConfig(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String code,
            @RequestBody ProviderDto.ConfigUpdateRequest request) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(providerService.updateProviderConfig(userId, code, request));
    }

    @Operation(summary = "Test provider API connection")
    @PostMapping("/{code}/test")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ProviderDto.TestResult> testProviderConnection(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String code,
            @RequestBody(required = false) ProviderDto.TestRequest request) {
        Long userId = extractUserId(authorization);
        if (request == null) {
            request = new ProviderDto.TestRequest();
        }
        return ApiResponse.success(providerService.testProviderConnection(userId, code, request));
    }

    private Long extractUserId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
