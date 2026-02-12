package com.paicoding.paiswitch.service;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.ApiKeyDto;
import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.repository.ApiKeyRepository;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ModelProviderRepository providerRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public ApiKeyDto.KeyInfo setApiKey(Long userId, ApiKeyDto.SetKeyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        ModelProvider provider = providerRepository.findByCode(request.getProviderCode())
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        String encryptedKey = encryptionService.encrypt(request.getApiKey());
        String keyHint = encryptionService.getKeyHint(request.getApiKey());

        ApiKey apiKey = apiKeyRepository.findByUserIdAndProviderId(userId, provider.getId())
                .orElse(ApiKey.builder()
                        .user(user)
                        .provider(provider)
                        .build());

        apiKey.setEncryptedKey(encryptedKey);
        apiKey.setKeyHint(keyHint);
        apiKey.setIsValid(true);

        apiKey = apiKeyRepository.save(apiKey);
        log.info("Set API key for provider: {} and user: {}", provider.getCode(), userId);

        return mapToKeyInfo(apiKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto.KeyInfo> getUserApiKeys(Long userId) {
        return apiKeyRepository.findByUserId(userId).stream()
                .map(this::mapToKeyInfo)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String getDecryptedApiKey(Long userId, String providerCode) {
        ApiKey apiKey = apiKeyRepository.findByUserIdAndProviderCode(userId, providerCode)
                .orElseThrow(() -> new BusinessException(ResponseCode.API_KEY_NOT_FOUND));

        if (!apiKey.getIsValid()) {
            throw new BusinessException(ResponseCode.API_KEY_INVALID);
        }

        return encryptionService.decrypt(apiKey.getEncryptedKey());
    }

    @Transactional
    public void deleteApiKey(Long userId, String providerCode) {
        ModelProvider provider = providerRepository.findByCode(providerCode)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        apiKeyRepository.deleteByUserIdAndProviderId(userId, provider.getId());
        log.info("Deleted API key for provider: {} and user: {}", providerCode, userId);
    }

    @Transactional
    public void updateLastUsedAt(Long userId, String providerCode) {
        apiKeyRepository.findByUserIdAndProviderCode(userId, providerCode)
                .ifPresent(apiKey -> {
                    apiKey.setLastUsedAt(LocalDateTime.now());
                    apiKeyRepository.save(apiKey);
                });
    }

    private ApiKeyDto.KeyInfo mapToKeyInfo(ApiKey apiKey) {
        return ApiKeyDto.KeyInfo.builder()
                .id(apiKey.getId())
                .providerId(apiKey.getProvider().getId())
                .providerCode(apiKey.getProvider().getCode())
                .providerName(apiKey.getProvider().getName())
                .keyHint(apiKey.getKeyHint())
                .isValid(apiKey.getIsValid())
                .lastUsedAt(apiKey.getLastUsedAt())
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
}
