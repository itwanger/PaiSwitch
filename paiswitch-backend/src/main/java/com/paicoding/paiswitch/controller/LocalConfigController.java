package com.paicoding.paiswitch.controller;

import com.paicoding.paiswitch.common.response.ApiResponse;
import com.paicoding.paiswitch.common.security.JwtTokenProvider;
import com.paicoding.paiswitch.domain.dto.ConfigDto;
import com.paicoding.paiswitch.service.LocalConfigService;
import com.paicoding.paiswitch.service.SwitchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Local Config", description = "Local configuration sync APIs")
@RestController
@RequestMapping("/api/v1/local")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LocalConfigController {

    private final LocalConfigService localConfigService;
    private final SwitchService switchService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Get local config info")
    @GetMapping("/config")
    public ApiResponse<LocalConfigService.LocalConfig> getLocalConfig() {
        return ApiResponse.success(localConfigService.readLocalConfig());
    }

    @Operation(summary = "Sync local config to server")
    @PostMapping("/sync")
    public ApiResponse<ConfigDto.ConfigInfo> syncLocalConfig(
            @RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);

        LocalConfigService.LocalConfig localConfig = localConfigService.readLocalConfig();

        // Switch to the provider from local config
        var result = switchService.switchToProvider(
                userId,
                localConfig.providerCode(),
                com.paicoding.paiswitch.domain.enums.SwitchType.MANUAL,
                null,
                "sync_from_local"
        );

        if (result.getSuccess()) {
            // Return updated config
            return ApiResponse.success("Synced from local config", null);
        } else {
            return ApiResponse.error(5001, result.getMessage());
        }
    }

    private Long extractUserId(String authorization) {
        String token = authorization.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
