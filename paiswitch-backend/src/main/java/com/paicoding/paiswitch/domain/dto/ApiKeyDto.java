package com.paicoding.paiswitch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ApiKeyDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SetKeyRequest {
        @NotBlank(message = "Provider code is required")
        private String providerCode;

        @NotBlank(message = "API key is required")
        private String apiKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeyInfo {
        private Long id;
        private Long providerId;
        private String providerCode;
        private String providerName;
        private String keyHint;
        private Boolean isValid;
        private LocalDateTime lastUsedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidateRequest {
        @NotBlank(message = "Provider code is required")
        private String providerCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidateResponse {
        private Boolean valid;
        private String message;
    }
}
