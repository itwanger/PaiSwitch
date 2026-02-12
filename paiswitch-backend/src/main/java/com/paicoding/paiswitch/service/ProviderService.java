package com.paicoding.paiswitch.service;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.ProviderDto;
import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.repository.ApiKeyRepository;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ModelProviderRepository providerRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public List<ProviderDto.ProviderInfo> getAllProviders() {
        return providerRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(this::mapToProviderInfo)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProviderDto.ProviderInfo> getProvidersForUser(Long userId) {
        List<ModelProvider> providers = providerRepository.findByIsActiveTrueOrderBySortOrderAsc();
        return providers.stream()
                .map(provider -> {
                    ProviderDto.ProviderInfo info = mapToProviderInfo(provider);
                    boolean hasApiKey = apiKeyRepository.existsByUserIdAndProviderId(userId, provider.getId());
                    return ProviderDto.ProviderInfo.builder()
                            .id(info.getId())
                            .code(info.getCode())
                            .name(info.getName())
                            .description(info.getDescription())
                            .baseUrl(info.getBaseUrl())
                            .modelName(info.getModelName())
                            .modelNameSmall(info.getModelNameSmall())
                            .isBuiltin(info.getIsBuiltin())
                            .isActive(info.getIsActive())
                            .sortOrder(info.getSortOrder())
                            .iconUrl(info.getIconUrl())
                            .hasApiKey(hasApiKey)
                            .createdAt(info.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProviderDto.ProviderInfo getProviderByCode(String code) {
        ModelProvider provider = providerRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));
        return mapToProviderInfo(provider);
    }

    @Transactional
    public ProviderDto.ProviderInfo createCustomProvider(Long userId, ProviderDto.CreateRequest request) {
        if (providerRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ResponseCode.PROVIDER_ALREADY_EXISTS);
        }

        ModelProvider provider = ModelProvider.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .baseUrl(request.getBaseUrl())
                .modelName(request.getModelName())
                .modelNameSmall(request.getModelNameSmall())
                .iconUrl(request.getIconUrl())
                .isBuiltin(false)
                .isActive(true)
                .sortOrder(100)
                .build();

        provider = providerRepository.save(provider);
        log.info("Created custom provider: {} for user: {}", provider.getCode(), userId);
        return mapToProviderInfo(provider);
    }

    @Transactional
    public ProviderDto.ProviderInfo updateProvider(Long userId, String code, ProviderDto.UpdateRequest request) {
        ModelProvider provider = providerRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        if (provider.getIsBuiltin()) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "Cannot modify built-in providers");
        }

        if (request.getName() != null) provider.setName(request.getName());
        if (request.getDescription() != null) provider.setDescription(request.getDescription());
        if (request.getBaseUrl() != null) provider.setBaseUrl(request.getBaseUrl());
        if (request.getModelName() != null) provider.setModelName(request.getModelName());
        if (request.getModelNameSmall() != null) provider.setModelNameSmall(request.getModelNameSmall());
        if (request.getIsActive() != null) provider.setIsActive(request.getIsActive());
        if (request.getIconUrl() != null) provider.setIconUrl(request.getIconUrl());

        provider = providerRepository.save(provider);
        log.info("Updated provider: {} by user: {}", provider.getCode(), userId);
        return mapToProviderInfo(provider);
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
