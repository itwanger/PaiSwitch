package com.paicoding.paiswitch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class SwitchDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SwitchRequest {
        @NotBlank(message = "Provider code is required")
        private String providerCode;

        private String clientInfo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SwitchResult {
        private Boolean success;
        private String message;
        private ProviderDto.ProviderInfo previousProvider;
        private ProviderDto.ProviderInfo currentProvider;
        private LocalDateTime switchedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NaturalLanguageRequest {
        @NotBlank(message = "Prompt is required")
        private String prompt;

        private String sessionId;
        private String clientInfo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NaturalLanguageResponse {
        private String aiResponse;
        private Boolean switchTriggered;
        private SwitchResult switchResult;
        private String sessionId;
    }
}
