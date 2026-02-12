package com.paicoding.paiswitch.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ConfigDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfigInfo {
        private Long id;
        private Long userId;
        private ProviderDto.ProviderInfo currentProvider;
        private Integer apiTimeout;
        private Map<String, Object> extraConfig;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @NotNull(message = "Provider ID is required")
        private Long providerId;

        private Integer apiTimeout;
        private Map<String, Object> extraConfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackupInfo {
        private Long id;
        private Long providerId;
        private String providerCode;
        private String providerName;
        private String backupName;
        private Map<String, Object> configContent;
        private String backupType;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackupListResponse {
        private List<BackupInfo> backups;
        private Long total;
    }
}
