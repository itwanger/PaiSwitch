package com.paicoding.paiswitch.service;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.ConfigDto;
import com.paicoding.paiswitch.domain.dto.ProviderDto;
import com.paicoding.paiswitch.domain.entity.ConfigBackup;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.domain.entity.UserConfig;
import com.paicoding.paiswitch.domain.enums.BackupType;
import com.paicoding.paiswitch.repository.ConfigBackupRepository;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
import com.paicoding.paiswitch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final UserConfigRepository configRepository;
    private final UserRepository userRepository;
    private final ModelProviderRepository providerRepository;
    private final ConfigBackupRepository backupRepository;

    @Transactional(readOnly = true)
    public ConfigDto.ConfigInfo getUserConfig(Long userId) {
        UserConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CONFIG_NOT_FOUND));

        return mapToConfigInfo(config);
    }

    @Transactional
    public ConfigDto.ConfigInfo updateUserConfig(Long userId, ConfigDto.UpdateRequest request) {
        UserConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CONFIG_NOT_FOUND));

        ModelProvider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        createBackup(userId, config, BackupType.AUTO_BEFORE_SWITCH, "Auto backup before config update");

        config.setCurrentProvider(provider);
        if (request.getApiTimeout() != null) {
            config.setApiTimeout(request.getApiTimeout());
        }
        if (request.getExtraConfig() != null) {
            config.setExtraConfig(request.getExtraConfig());
        }

        config = configRepository.save(config);
        log.info("Updated config for user: {}, provider: {}", userId, provider.getCode());

        return mapToConfigInfo(config);
    }

    @Transactional
    public void createBackup(Long userId, UserConfig config, BackupType backupType, String backupName) {
        Map<String, Object> configContent = new HashMap<>();
        configContent.put("providerId", config.getCurrentProvider().getId());
        configContent.put("providerCode", config.getCurrentProvider().getCode());
        configContent.put("apiTimeout", config.getApiTimeout());
        configContent.put("extraConfig", config.getExtraConfig());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        ConfigBackup backup = ConfigBackup.builder()
                .user(user)
                .provider(config.getCurrentProvider())
                .backupName(backupName)
                .configContent(configContent)
                .backupType(backupType)
                .build();

        backupRepository.save(backup);
        log.info("Created backup for user: {}, type: {}", userId, backupType);
    }

    @Transactional(readOnly = true)
    public ConfigDto.BackupListResponse getBackups(Long userId, int page, int size) {
        List<ConfigDto.BackupInfo> backups = backupRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(this::mapToBackupInfo)
                .collect(Collectors.toList());

        long total = backupRepository.findByUserIdOrderByCreatedAtDesc(userId).size();

        return ConfigDto.BackupListResponse.builder()
                .backups(backups)
                .total(total)
                .build();
    }

    @Transactional
    public ConfigDto.ConfigInfo restoreBackup(Long userId, Long backupId) {
        ConfigBackup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new BusinessException(ResponseCode.BACKUP_NOT_FOUND));

        if (!backup.getUser().getId().equals(userId)) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "Cannot restore another user's backup");
        }

        UserConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CONFIG_NOT_FOUND));

        Map<String, Object> content = backup.getConfigContent();
        Long providerId = ((Number) content.get("providerId")).longValue();
        Integer apiTimeout = (Integer) content.get("apiTimeout");
        @SuppressWarnings("unchecked")
        Map<String, Object> extraConfig = (Map<String, Object>) content.get("extraConfig");

        ModelProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        config.setCurrentProvider(provider);
        config.setApiTimeout(apiTimeout);
        if (extraConfig != null) {
            config.setExtraConfig(extraConfig);
        }

        config = configRepository.save(config);
        log.info("Restored backup: {} for user: {}", backupId, userId);

        return mapToConfigInfo(config);
    }

    private ConfigDto.ConfigInfo mapToConfigInfo(UserConfig config) {
        return ConfigDto.ConfigInfo.builder()
                .id(config.getId())
                .userId(config.getUser().getId())
                .currentProvider(mapToProviderInfo(config.getCurrentProvider()))
                .apiTimeout(config.getApiTimeout())
                .extraConfig(config.getExtraConfig())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private ConfigDto.BackupInfo mapToBackupInfo(ConfigBackup backup) {
        return ConfigDto.BackupInfo.builder()
                .id(backup.getId())
                .providerId(backup.getProvider().getId())
                .providerCode(backup.getProvider().getCode())
                .providerName(backup.getProvider().getName())
                .backupName(backup.getBackupName())
                .configContent(backup.getConfigContent())
                .backupType(backup.getBackupType().name())
                .createdAt(backup.getCreatedAt())
                .build();
    }

    private ProviderDto.ProviderInfo mapToProviderInfo(ModelProvider provider) {
        return ProviderDto.ProviderInfo.builder()
                .id(provider.getId())
                .code(provider.getCode())
                .name(provider.getName())
                .description(provider.getDescription())
                .baseUrl(provider.getBaseUrl())
                .modelName(provider.getModelName())
                .modelNameSmall(provider.getModelNameSmall())
                .isBuiltin(provider.getIsBuiltin())
                .isActive(provider.getIsActive())
                .sortOrder(provider.getSortOrder())
                .iconUrl(provider.getIconUrl())
                .createdAt(provider.getCreatedAt())
                .build();
    }
}
