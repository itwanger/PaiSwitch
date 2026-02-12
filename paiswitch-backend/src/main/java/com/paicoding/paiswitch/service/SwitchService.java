package com.paicoding.paiswitch.service;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.ConfigDto;
import com.paicoding.paiswitch.domain.dto.ProviderDto;
import com.paicoding.paiswitch.domain.dto.SwitchDto;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.SwitchHistory;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.domain.entity.UserConfig;
import com.paicoding.paiswitch.domain.enums.BackupType;
import com.paicoding.paiswitch.domain.enums.SwitchType;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.SwitchHistoryRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
import com.paicoding.paiswitch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwitchService {

    private final UserRepository userRepository;
    private final UserConfigRepository configRepository;
    private final ModelProviderRepository providerRepository;
    private final SwitchHistoryRepository switchHistoryRepository;
    private final ConfigService configService;
    private final ApiKeyService apiKeyService;
    private final SettingsWriterService settingsWriterService;

    @Transactional
    public SwitchDto.SwitchResult switchToProvider(Long userId, String providerCode, SwitchType switchType,
                                                     String aiPrompt, String clientInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        ModelProvider targetProvider = providerRepository.findByCode(providerCode)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        if (!targetProvider.getIsActive()) {
            throw new BusinessException(ResponseCode.PROVIDER_INACTIVE);
        }

        UserConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CONFIG_NOT_FOUND));

        ModelProvider fromProvider = config.getCurrentProvider();

        if (fromProvider.getId().equals(targetProvider.getId())) {
            return SwitchDto.SwitchResult.builder()
                    .success(true)
                    .message("Already using " + targetProvider.getName())
                    .currentProvider(mapToProviderInfo(targetProvider))
                    .switchedAt(LocalDateTime.now())
                    .build();
        }

        configService.createBackup(userId, config, BackupType.AUTO_BEFORE_SWITCH,
                "Auto backup before switching to " + targetProvider.getName());

        SwitchHistory history = SwitchHistory.builder()
                .user(user)
                .fromProvider(fromProvider)
                .toProvider(targetProvider)
                .switchType(switchType)
                .aiPrompt(aiPrompt)
                .clientInfo(clientInfo)
                .build();

        try {
            config.setCurrentProvider(targetProvider);
            configRepository.save(config);

            apiKeyService.updateLastUsedAt(userId, providerCode);

            // Write to settings.json with latest provider config from database
            settingsWriterService.writeToSettings(userId, targetProvider);

            history.setSuccess(true);
            switchHistoryRepository.save(history);

            log.info("Switched user {} from {} to {}", userId, fromProvider.getCode(), providerCode);

            return SwitchDto.SwitchResult.builder()
                    .success(true)
                    .message("Successfully switched to " + targetProvider.getName())
                    .previousProvider(mapToProviderInfo(fromProvider))
                    .currentProvider(mapToProviderInfo(targetProvider))
                    .switchedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            history.setSuccess(false);
            history.setErrorMessage(e.getMessage());
            switchHistoryRepository.save(history);

            log.error("Failed to switch user {} to {}: {}", userId, providerCode, e.getMessage());

            return SwitchDto.SwitchResult.builder()
                    .success(false)
                    .message("Failed to switch: " + e.getMessage())
                    .currentProvider(mapToProviderInfo(fromProvider))
                    .switchedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Transactional
    public SwitchDto.SwitchResult switchToProvider(Long userId, SwitchDto.SwitchRequest request) {
        return switchToProvider(userId, request.getProviderCode(), SwitchType.MANUAL, null, request.getClientInfo());
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
