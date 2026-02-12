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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ModelProviderRepository providerRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final EncryptionService encryptionService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

    /**
     * Update provider configuration (baseUrl, modelName, modelNameSmall).
     * This only updates the database, does NOT sync to settings.json.
     * The settings.json will be updated only when user switches to the model.
     */
    @Transactional
    public ProviderDto.ProviderInfo updateProviderConfig(Long userId, String code, ProviderDto.ConfigUpdateRequest request) {
        ModelProvider provider = providerRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        if (request.getBaseUrl() != null && !request.getBaseUrl().isEmpty()) {
            provider.setBaseUrl(request.getBaseUrl());
        }
        if (request.getModelName() != null && !request.getModelName().isEmpty()) {
            provider.setModelName(request.getModelName());
        }
        if (request.getModelNameSmall() != null) {
            provider.setModelNameSmall(request.getModelNameSmall().isEmpty() ? null : request.getModelNameSmall());
        }

        provider = providerRepository.save(provider);
        log.info("Updated provider config: {} by user: {}, model: {}", provider.getCode(), userId, provider.getModelName());
        return mapToProviderInfo(provider);
    }

    /**
     * Test API connection for a provider.
     * Uses provided config or falls back to stored config.
     */
    public ProviderDto.TestResult testProviderConnection(Long userId, String code, ProviderDto.TestRequest request) {
        ModelProvider provider = providerRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        // Use request values or fall back to stored values
        String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isEmpty()
                ? request.getBaseUrl()
                : provider.getBaseUrl();
        String modelName = request.getModelName() != null && !request.getModelName().isEmpty()
                ? request.getModelName()
                : provider.getModelName();

        // Get API key from request or from stored keys
        String apiKey = request.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            Optional<ApiKey> storedKey = apiKeyRepository.findByUserIdAndProviderId(userId, provider.getId());
            if (storedKey.isPresent()) {
                apiKey = encryptionService.decrypt(storedKey.get().getEncryptedKey());
            }
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("API Key 未配置")
                    .build();
        }

        return performTestRequest(baseUrl, modelName, apiKey);
    }

    private ProviderDto.TestResult performTestRequest(String baseUrl, String modelName, String apiKey) {
        long startTime = System.currentTimeMillis();

        try {
            // Normalize base URL
            String normalizedUrl = baseUrl;
            if (!normalizedUrl.startsWith("http")) {
                normalizedUrl = "https://" + normalizedUrl;
            }
            if (normalizedUrl.endsWith("/")) {
                normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
            }

            // Build test request - simple models list or messages endpoint
            String testUrl = normalizedUrl + "/v1/messages";

            String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 10,
                    "messages": [{"role": "user", "content": "Hi"}]
                }
                """.formatted(modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ProviderDto.TestResult.builder()
                        .success(true)
                        .message("连接成功")
                        .modelName(modelName)
                        .responseTimeMs(responseTime)
                        .build();
            } else if (response.statusCode() == 401) {
                return ProviderDto.TestResult.builder()
                        .success(false)
                        .message("API Key 无效")
                        .responseTimeMs(responseTime)
                        .build();
            } else if (response.statusCode() == 404) {
                return ProviderDto.TestResult.builder()
                        .success(false)
                        .message("模型不存在或 Base URL 错误")
                        .responseTimeMs(responseTime)
                        .build();
            } else {
                String errorMsg = extractErrorMessage(response.body());
                return ProviderDto.TestResult.builder()
                        .success(false)
                        .message("请求失败: " + errorMsg)
                        .responseTimeMs(responseTime)
                        .build();
            }

        } catch (java.net.ConnectException e) {
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("无法连接到服务器，请检查 Base URL")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (java.net.SocketTimeoutException e) {
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("连接超时，请检查网络或 Base URL")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("Test connection failed: {}", e.getMessage());
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("测试失败: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "未知错误";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseBody);
            if (root.has("error")) {
                com.fasterxml.jackson.databind.JsonNode error = root.get("error");
                if (error.has("message")) {
                    return error.get("message").asText();
                }
                return error.asText();
            }
        } catch (Exception ignored) {
        }
        if (responseBody.length() > 100) {
            return responseBody.substring(0, 100) + "...";
        }
        return responseBody;
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
