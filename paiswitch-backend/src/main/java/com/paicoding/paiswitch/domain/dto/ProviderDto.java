package com.paicoding.paiswitch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ProviderDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProviderInfo {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String baseUrl;
        private String modelName;
        private String modelNameSmall;
        private Boolean isBuiltin;
        private Boolean isActive;
        private Integer sortOrder;
        private String iconUrl;
        private Boolean hasApiKey;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "Code is required")
        @Size(max = 50, message = "Code must be at most 50 characters")
        private String code;

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        private String name;

        @Size(max = 500, message = "Description must be at most 500 characters")
        private String description;

        @NotBlank(message = "Base URL is required")
        private String baseUrl;

        @NotBlank(message = "Model name is required")
        @Size(max = 100, message = "Model name must be at most 100 characters")
        private String modelName;

        @Size(max = 100, message = "Small model name must be at most 100 characters")
        private String modelNameSmall;

        private String iconUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String name;
        private String description;
        private String baseUrl;
        private String modelName;
        private String modelNameSmall;
        private Boolean isActive;
        private String iconUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfigUpdateRequest {
        private String baseUrl;
        private String modelName;
        private String modelNameSmall;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TestRequest {
        private String baseUrl;
        private String modelName;
        private String apiKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TestResult {
        private boolean success;
        private String message;
        private String modelName;
        private Long responseTimeMs;
    }
}
