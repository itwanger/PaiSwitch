package com.paicoding.paiswitch.service;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.ProviderDto;
import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.enums.TargetTool;
import com.paicoding.paiswitch.proxy.ChatResponseValidator;
import com.paicoding.paiswitch.proxy.UpstreamRequestConfig;
import com.paicoding.paiswitch.repository.ApiKeyRepository;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
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

    private static final String CLAUDE_PROVIDER_CODE = "claude";
    private static final String CLAUDE_OFFICIAL_BASE_URL = "https://api.anthropic.com";
    private static final String CLAUDE_DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final String CLAUDE_DEFAULT_SMALL_MODEL = "claude-3-5-haiku-latest";
    private static final String OPENAI_WIRE_API = "openai";

    private enum ProviderApiStyle {
        OPENROUTER,
        ANTHROPIC,
        OPENAI,
        RESPONSES
    }

    private final ModelProviderRepository providerRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserConfigRepository configRepository;
    private final EncryptionService encryptionService;
    private final SettingsWriterService settingsWriterService;
    private final CodexSettingsWriterService codexSettingsWriterService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Transactional(readOnly = true)
    public List<ProviderDto.ProviderInfo> getAllProviders(TargetTool tool) {
        return providerRepository.findByTargetToolAndIsActiveTrueOrderBySortOrderAsc(tool).stream()
                .map(this::mapToProviderInfo)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProviderDto.ProviderInfo> getProvidersForUser(Long userId, TargetTool tool) {
        List<ModelProvider> providers = providerRepository.findByTargetToolAndIsActiveTrueOrderBySortOrderAsc(tool);
        return providers.stream()
                .map(provider -> {
                    ProviderDto.ProviderInfo info = mapToProviderInfo(provider);
                    boolean hasApiKey = apiKeyRepository.existsByUserIdAndProviderId(userId, provider.getId());
                    info.setHasApiKey(hasApiKey);
                    return info;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProviderDto.ProviderInfo getProviderByCode(String code, TargetTool tool) {
        ModelProvider provider = providerRepository.findByCodeAndTargetTool(code, tool)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));
        return mapToProviderInfo(provider);
    }

    @Transactional
    public ProviderDto.ProviderInfo createCustomProvider(Long userId, ProviderDto.CreateRequest request, TargetTool tool) {
        String code = sanitizeCode(request.getCode());
        if (providerRepository.existsByCodeAndTargetTool(code, tool)) {
            throw new BusinessException(ResponseCode.PROVIDER_ALREADY_EXISTS);
        }

        ModelProvider provider = ModelProvider.builder()
                .code(code)
                .name(sanitizeOptionalText(request.getName()))
                .description(sanitizeOptionalText(request.getDescription()))
                .baseUrl(sanitizeOptionalText(request.getBaseUrl()))
                .modelName(sanitizeText(request.getModelName()))
                .modelNameSmall(sanitizeOptionalText(request.getModelNameSmall()))
                .iconUrl(sanitizeOptionalText(request.getIconUrl()))
                .isBuiltin(false)
                .isActive(true)
                .sortOrder(100)
                .targetTool(tool)
                .wireApi(tool == TargetTool.CODEX ? "chat" : OPENAI_WIRE_API)
                .providerKey(tool == TargetTool.CODEX ? "custom_" + code : null)
                .build();

        provider = providerRepository.save(provider);
        log.info("Created custom provider: {} ({}) for user: {}", provider.getCode(), tool, userId);
        return mapToProviderInfo(provider);
    }

    @Transactional
    public ProviderDto.ProviderInfo updateProvider(Long userId, String code, TargetTool tool, ProviderDto.UpdateRequest request) {
        ModelProvider provider = providerRepository.findByCodeAndTargetTool(code, tool)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        if (provider.getIsBuiltin()) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "Cannot modify built-in providers");
        }

        if (request.getName() != null) provider.setName(sanitizeOptionalText(request.getName()));
        if (request.getDescription() != null) provider.setDescription(sanitizeOptionalText(request.getDescription()));
        if (request.getBaseUrl() != null) provider.setBaseUrl(sanitizeOptionalText(request.getBaseUrl()));
        if (request.getModelName() != null) provider.setModelName(sanitizeOptionalText(request.getModelName()));
        if (request.getModelNameSmall() != null) provider.setModelNameSmall(sanitizeOptionalText(request.getModelNameSmall()));
        if (request.getIsActive() != null) provider.setIsActive(request.getIsActive());
        if (request.getIconUrl() != null) provider.setIconUrl(sanitizeOptionalText(request.getIconUrl()));

        provider = providerRepository.save(provider);
        log.info("Updated provider: {} ({}) by user: {}", provider.getCode(), tool, userId);
        return mapToProviderInfo(provider);
    }

    /**
     * Delete a custom provider. Built-in providers cannot be removed, and a
     * provider that is currently active for the user must be switched away first.
     * <p>
     * Implemented as a soft delete ({@code is_active = false}) so existing
     * {@code switch_history} references stay intact; the provider simply drops out
     * of every list (all queries filter on {@code is_active = true}). The stored
     * API key is removed so no orphaned secret lingers.
     */
    @Transactional
    public void deleteCustomProvider(Long userId, String code, TargetTool tool) {
        ModelProvider provider = providerRepository.findByCodeAndTargetTool(code, tool)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        if (Boolean.TRUE.equals(provider.getIsBuiltin())) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "无法删除内置供应商");
        }

        configRepository.findByUserId(userId).ifPresent(config -> {
            boolean isCurrentClaude = config.getCurrentProvider() != null
                    && provider.getId().equals(config.getCurrentProvider().getId());
            boolean isCurrentCodex = config.getCurrentCodexProvider() != null
                    && provider.getId().equals(config.getCurrentCodexProvider().getId());
            if (isCurrentClaude || isCurrentCodex) {
                throw new BusinessException(ResponseCode.CONFLICT, "该供应商正在使用中，请先切换到其他模型再删除");
            }
        });

        apiKeyRepository.deleteByUserIdAndProviderId(userId, provider.getId());
        provider.setIsActive(false);
        providerRepository.save(provider);
        log.info("Deleted custom provider: {} ({}) by user: {}", code, tool, userId);
    }

    /**
     * Update provider configuration (baseUrl, modelName, modelNameSmall).
     * Syncs the corresponding settings file when the updated provider is currently active.
     */
    @Transactional
    public ProviderDto.ProviderInfo updateProviderConfig(Long userId, String code, TargetTool tool, ProviderDto.ConfigUpdateRequest request) {
        ModelProvider provider = providerRepository.findByCodeAndTargetTool(code, tool)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        String sanitizedBaseUrl = sanitizeOptionalText(request.getBaseUrl());
        String sanitizedModelName = sanitizeOptionalText(request.getModelName());
        String sanitizedModelNameSmall = sanitizeOptionalText(request.getModelNameSmall());

        boolean isClaudeOfficial = tool == TargetTool.CLAUDE_CODE && CLAUDE_PROVIDER_CODE.equals(code);
        if (isClaudeOfficial) {
            if (!CLAUDE_OFFICIAL_BASE_URL.equals(provider.getBaseUrl())) {
                provider.setBaseUrl(CLAUDE_OFFICIAL_BASE_URL);
            }
            if (!CLAUDE_DEFAULT_MODEL.equals(provider.getModelName())) {
                provider.setModelName(CLAUDE_DEFAULT_MODEL);
            }
            if (!CLAUDE_DEFAULT_SMALL_MODEL.equals(provider.getModelNameSmall())) {
                provider.setModelNameSmall(CLAUDE_DEFAULT_SMALL_MODEL);
            }
        } else if (sanitizedBaseUrl != null && !sanitizedBaseUrl.isEmpty()) {
            provider.setBaseUrl(sanitizedBaseUrl);
        }

        if (!isClaudeOfficial && request.getModelName() != null) {
            provider.setModelName(sanitizedModelName == null ? "" : sanitizedModelName);
        }
        if (!isClaudeOfficial && request.getModelNameSmall() != null) {
            provider.setModelNameSmall(sanitizedModelNameSmall == null || sanitizedModelNameSmall.isEmpty() ? null : sanitizedModelNameSmall);
        }

        provider = providerRepository.save(provider);
        syncSettingsIfCurrentProvider(userId, provider);
        log.info("Updated provider config: {} ({}) by user: {}, model: {}", provider.getCode(), tool, userId, provider.getModelName());
        return mapToProviderInfo(provider);
    }

    private void syncSettingsIfCurrentProvider(Long userId, ModelProvider provider) {
        configRepository.findByUserId(userId).ifPresent(config -> {
            if (provider.getTargetTool() == TargetTool.CLAUDE_CODE
                    && config.getCurrentProvider() != null
                    && provider.getId().equals(config.getCurrentProvider().getId())) {
                settingsWriterService.writeToSettings(userId, provider);
            } else if (provider.getTargetTool() == TargetTool.CODEX
                    && config.getCurrentCodexProvider() != null
                    && provider.getId().equals(config.getCurrentCodexProvider().getId())) {
                codexSettingsWriterService.writeToSettings(userId, provider);
            }
        });
    }

    /**
     * Test API connection for a provider.
     * Uses provided config or falls back to stored config.
     */
    public ProviderDto.TestResult testProviderConnection(Long userId, String code, TargetTool tool, ProviderDto.TestRequest request) {
        ModelProvider provider = providerRepository.findByCodeAndTargetTool(code, tool)
                .orElseThrow(() -> new BusinessException(ResponseCode.PROVIDER_NOT_FOUND));

        // Use request values or fall back to stored values
        String baseUrl = request.getBaseUrl() != null && !request.getBaseUrl().isEmpty()
                ? request.getBaseUrl()
                : provider.getBaseUrl();
        String modelName = request.getModelName() != null && !request.getModelName().isEmpty()
                ? request.getModelName()
                : provider.getModelName();

        if (baseUrl == null || baseUrl.isBlank()) {
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("Base URL 未配置")
                    .build();
        }
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        UpstreamRequestConfig upstreamConfig = UpstreamRequestConfig.fromBaseUrl(normalizedUrl);
        ProviderApiStyle apiStyle = resolveApiStyle(provider, upstreamConfig.baseUrl(), tool);
        if (requiresModelNameForTest(apiStyle, upstreamConfig.baseUrl()) && (modelName == null || modelName.isBlank())) {
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("模型名称未配置")
                    .build();
        }

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

        return performTestRequest(provider, normalizedUrl, modelName, apiKey, tool);
    }

    private ProviderDto.TestResult performTestRequest(ModelProvider provider, String normalizedUrl, String modelName, String apiKey, TargetTool tool) {
        long startTime = System.currentTimeMillis();
        String testUrl = "";
        String providerCode = provider.getCode();

        try {
            String normalizedApiKey = normalizeApiKey(apiKey);
            UpstreamRequestConfig upstreamConfig = UpstreamRequestConfig.fromBaseUrl(normalizedUrl);
            String upstreamBaseUrl = upstreamConfig.baseUrl();
            ProviderApiStyle apiStyle = resolveApiStyle(provider, upstreamBaseUrl, tool);

            testUrl = switch (apiStyle) {
                case OPENROUTER, OPENAI -> buildChatCompletionsUrl(upstreamBaseUrl);
                case ANTHROPIC -> buildMessagesTestUrl(upstreamBaseUrl);
                case RESPONSES -> buildResponsesTestUrl(upstreamBaseUrl);
            };
            String requestBody = switch (apiStyle) {
                case OPENROUTER -> buildOpenRouterTestRequestBody(modelName);
                case OPENAI -> buildOpenAiCompatibleTestRequestBody(modelName);
                case ANTHROPIC -> buildAnthropicTestRequestBody(modelName);
                case RESPONSES -> buildResponsesTestRequestBody(modelName);
            };

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30));
            upstreamConfig.headers().forEach(requestBuilder::header);

            switch (apiStyle) {
                case OPENROUTER, OPENAI, RESPONSES ->
                        requestBuilder.header("Authorization", "Bearer " + normalizedApiKey);
                case ANTHROPIC ->
                        requestBuilder
                                .header("x-api-key", normalizedApiKey)
                                .header("anthropic-version", "2023-06-01");
            }

            log.info("Test request -> provider={}, url={}, auth={}, apiKey={}, body={}",
                    providerCode,
                    testUrl,
                    authHeaderName(apiStyle),
                    maskApiKey(normalizedApiKey),
                    singleLine(requestBody));

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("Test response <- provider={}, url={}, status={}, body={}",
                    providerCode,
                    testUrl,
                    response.statusCode(),
                    truncate(response.body(), 4000));

            if (apiStyle == ProviderApiStyle.OPENROUTER && shouldRetryOpenRouterWithAvailableProviders(response)) {
                List<String> availableProviders = extractOpenRouterAvailableProviders(response.body());
                if (!availableProviders.isEmpty()) {
                    String retryRequestBody = buildOpenRouterTestRequestBody(modelName, availableProviders);
                    HttpRequest retryRequest = buildHttpRequest(testUrl, retryRequestBody, apiStyle, normalizedApiKey, upstreamConfig);
                    log.info("Test retry request -> provider={}, url={}, order={}, body={}",
                            providerCode,
                            testUrl,
                            availableProviders,
                            singleLine(retryRequestBody));
                    HttpResponse<String> retryResponse = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
                    log.info("Test retry response <- provider={}, url={}, status={}, body={}",
                            providerCode,
                            testUrl,
                            retryResponse.statusCode(),
                            truncate(retryResponse.body(), 4000));
                    return buildTestResult(retryResponse, modelName, startTime, apiStyle);
                }
            }

            return buildTestResult(response, modelName, startTime, apiStyle);

        } catch (java.net.ConnectException e) {
            log.warn("Test connection connect exception: provider={}, url={}, message={}", providerCode, testUrl, e.getMessage());
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("无法连接到服务器，请检查 Base URL")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (java.net.SocketTimeoutException e) {
            log.warn("Test connection timeout: provider={}, url={}, message={}", providerCode, testUrl, e.getMessage());
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("连接超时，请检查网络或 Base URL")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("Test connection failed: provider={}, url={}, message={}", providerCode, testUrl, e.getMessage(), e);
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("测试失败: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "***";
        }
        int length = apiKey.length();
        if (length <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(length - 4);
    }

    private String singleLine(String value) {
        if (value == null) {
            return "";
        }
        return sanitizeText(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String normalized = sanitizeText(apiKey);
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return normalized.substring(7).trim();
        }
        return normalized;
    }

    private boolean isOpenRouterProvider(String providerCode, String normalizedUrl) {
        return "openrouter".equalsIgnoreCase(providerCode) || normalizedUrl.toLowerCase().contains("openrouter.ai");
    }

    private ProviderApiStyle resolveApiStyle(ModelProvider provider, String normalizedUrl, TargetTool tool) {
        String providerCode = provider.getCode();
        String lowerUrl = normalizedUrl.toLowerCase();
        if (isOpenRouterProvider(providerCode, normalizedUrl)) {
            return ProviderApiStyle.OPENROUTER;
        }
        // Codex providers that natively speak the Responses API (e.g. StepFun) must
        // be tested against /responses, not /chat/completions — otherwise the test
        // silently exercises the wrong endpoint and tells us nothing about the path
        // Codex actually uses.
        if (tool == TargetTool.CODEX && speaksResponsesNatively(provider)) {
            return ProviderApiStyle.RESPONSES;
        }
        if (tool == TargetTool.CLAUDE_CODE && isOpenAiWireProvider(provider)) {
            return ProviderApiStyle.OPENAI;
        }
        // Claude Code only ever sends Anthropic Messages API requests against the
        // configured base_url, so testing must exercise the same shape — even when
        // the URL path doesn't visibly look Anthropic (e.g. `/agent`, `/api/coding`).
        // Otherwise a non-Anthropic upstream passes an OpenAI-style test, then
        // crashes Claude Code at runtime on `usage.input_tokens`.
        if (tool == TargetTool.CLAUDE_CODE) {
            return ProviderApiStyle.ANTHROPIC;
        }
        if (isKimiCodingProvider(normalizedUrl)) {
            return ProviderApiStyle.ANTHROPIC;
        }
        if (isAnthropicCompatibleProvider(lowerUrl)) {
            return ProviderApiStyle.ANTHROPIC;
        }
        return ProviderApiStyle.OPENAI;
    }

    private boolean speaksResponsesNatively(ModelProvider provider) {
        String wireApi = provider.getWireApi();
        return wireApi != null && "responses".equalsIgnoreCase(wireApi.trim());
    }

    private boolean isOpenAiWireProvider(ModelProvider provider) {
        String wireApi = provider.getWireApi();
        return wireApi != null && OPENAI_WIRE_API.equalsIgnoreCase(wireApi.trim())
                || provider.getTargetTool() == TargetTool.CLAUDE_CODE
                && !Boolean.TRUE.equals(provider.getIsBuiltin())
                && (wireApi == null || wireApi.isBlank());
    }

    private boolean isAnthropicCompatibleProvider(String lowerUrl) {
        return lowerUrl.contains("anthropic.com")
                || lowerUrl.contains("/anthropic")
                || lowerUrl.endsWith("/messages")
                || lowerUrl.endsWith("/v1/messages");
    }

    private boolean isKimiCodingProvider(String normalizedUrl) {
        return normalizedUrl.toLowerCase().contains("api.kimi.com/coding");
    }

    private boolean requiresModelNameForTest(ProviderApiStyle apiStyle, String normalizedUrl) {
        if (apiStyle == ProviderApiStyle.ANTHROPIC && isKimiCodingProvider(normalizedUrl)) {
            return false;
        }
        return true;
    }

    private String authHeaderName(ProviderApiStyle apiStyle) {
        return apiStyle == ProviderApiStyle.ANTHROPIC ? "x-api-key" : "Authorization: Bearer";
    }

    private String buildChatCompletionsUrl(String normalizedUrl) {
        if (normalizedUrl.endsWith("/v1/chat/completions") || normalizedUrl.endsWith("/chat/completions")) {
            return normalizedUrl;
        }
        if (normalizedUrl.endsWith("/v1") || normalizedUrl.endsWith("/v2") || normalizedUrl.endsWith("/api/v3")) {
            return normalizedUrl + "/chat/completions";
        }
        return normalizedUrl + "/v1/chat/completions";
    }

    private String buildResponsesTestUrl(String normalizedUrl) {
        if (normalizedUrl.endsWith("/responses")) {
            return normalizedUrl;
        }
        if (normalizedUrl.endsWith("/v1") || normalizedUrl.endsWith("/v2") || normalizedUrl.endsWith("/api/v3")) {
            return normalizedUrl + "/responses";
        }
        return normalizedUrl + "/v1/responses";
    }

    private String buildResponsesTestRequestBody(String modelName) {
        return """
            {
                "model": "%s",
                "input": "Hi",
                "max_output_tokens": 16
            }
            """.formatted(modelName);
    }

    private String buildOpenAiCompatibleTestRequestBody(String modelName) {
        return """
            {
                "model": "%s",
                "max_tokens": 10,
                "messages": [{"role": "user", "content": "Hi"}]
            }
            """.formatted(modelName);
    }

    private String buildAnthropicTestRequestBody(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return """
                {
                    "max_tokens": 10,
                    "messages": [{"role": "user", "content": "Hi"}]
                }
                """;
        }
        return """
            {
                "model": "%s",
                "max_tokens": 10,
                "messages": [{"role": "user", "content": "Hi"}]
            }
            """.formatted(modelName);
    }

    private String buildOpenRouterTestRequestBody(String modelName) {
        String providerHint = extractModelProviderHint(modelName);
        return buildOpenRouterTestRequestBody(modelName,
                providerHint == null ? List.of() : List.of(providerHint));
    }

    private String buildOpenRouterTestRequestBody(String modelName, List<String> providerOrder) {
        if (providerOrder == null || providerOrder.isEmpty()) {
            return """
                {
                    "model": "%s",
                    "max_tokens": 10,
                    "messages": [{"role": "user", "content": "Hi"}]
                }
                """.formatted(modelName);
        }

        String orderJson = providerOrder.stream()
                .map(provider -> "\"" + provider + "\"")
                .collect(Collectors.joining(", "));
        return """
            {
                "model": "%s",
                "max_tokens": 10,
                "messages": [{"role": "user", "content": "Hi"}],
                "provider": {
                    "order": [%s],
                    "allow_fallbacks": true
                }
            }
            """.formatted(modelName, orderJson);
    }

    private String extractModelProviderHint(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        String normalized = modelName.trim();
        int slashIndex = normalized.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        String providerHint = normalized.substring(0, slashIndex).trim().toLowerCase();
        return providerHint.isEmpty() ? null : providerHint;
    }

    private ProviderDto.TestResult buildTestResult(HttpResponse<String> response, String modelName, long startTime, ProviderApiStyle apiStyle) {
        long responseTime = System.currentTimeMillis() - startTime;

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // For Claude Code (Anthropic Messages API) responses, verify the body
            // includes `usage.input_tokens`. Without it Claude Code crashes at
            // runtime with `undefined is not an object (evaluating '_.input_tokens')`.
            if (apiStyle == ProviderApiStyle.ANTHROPIC) {
                String usageError = validateAnthropicUsage(response.body());
                if (usageError != null) {
                    return ProviderDto.TestResult.builder()
                            .success(false)
                            .message(usageError)
                            .responseTimeMs(responseTime)
                            .build();
                }
            } else if (apiStyle == ProviderApiStyle.RESPONSES) {
                String responsesError = validateResponsesBody(response.body());
                if (responsesError != null) {
                    return ProviderDto.TestResult.builder()
                            .success(false)
                            .message(responsesError)
                            .responseTimeMs(responseTime)
                            .build();
                }
            } else {
                // OpenAI-compatible upstreams: some providers (apifree.ai / SkyClaw)
                // wrap errors in HTTP 200 with a body-level `error` object. Without
                // this check the test mis-reports "success" while real Codex/Claude
                // traffic silently returns nothing.
                String bodyError = validateOpenAiChatBody(response.body());
                if (bodyError != null) {
                    return ProviderDto.TestResult.builder()
                            .success(false)
                            .message(bodyError)
                            .responseTimeMs(responseTime)
                            .build();
                }
            }
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
            String errorMsg = extractErrorMessage(response.body());
            return ProviderDto.TestResult.builder()
                    .success(false)
                    .message("请求失败: " + errorMsg)
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
    }

    /**
     * Returns null when the Anthropic Messages response includes the
     * {@code usage.input_tokens} field Claude Code requires, otherwise returns
     * a human-readable failure message describing the specific gap.
     */
    private String validateAnthropicUsage(String body) {
        if (body == null || body.isBlank()) {
            return "响应体为空，无法读取 usage.input_tokens（Claude Code 会崩）";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode usage = root.get("usage");
            if (usage == null || usage.isNull() || !usage.isObject()) {
                return "供应商响应缺少 usage 对象，不符合 Anthropic Messages API，Claude Code 会报 '_.input_tokens' 错误";
            }
            com.fasterxml.jackson.databind.JsonNode inputTokens = usage.get("input_tokens");
            if (inputTokens == null || inputTokens.isNull() || !inputTokens.isNumber()) {
                String hint = usage.has("prompt_tokens")
                        ? "（看到 prompt_tokens —— 这是 OpenAI 风格，不是 Anthropic）"
                        : "";
                return "响应里 usage.input_tokens 缺失或非数字" + hint + "，Claude Code 会崩";
            }
            return null;
        } catch (Exception e) {
            return "响应不是合法 JSON，无法验证 usage.input_tokens：" + e.getMessage();
        }
    }

    /**
     * Catches the "HTTP 200 + body-level error" pattern emitted by some
     * OpenAI-compatible upstreams (apifree.ai / SkyClaw wrap errors as
     * {@code {"code":401,"error":{...}}}). Returns null when the body looks
     * like a usable chat-completion response.
     */
    private String validateOpenAiChatBody(String body) {
        if (body == null || body.isBlank()) {
            return "响应体为空，无法判断是否是有效的 chat-completion";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
            return ChatResponseValidator.describeBodyLevelError(root);
        } catch (Exception e) {
            return "响应不是合法 JSON：" + e.getMessage();
        }
    }

    /**
     * Validates a Responses API reply. Returns null when the body looks like a
     * usable {@code response} object, otherwise a human-readable failure message.
     * A native Responses endpoint returns a top-level {@code object}/{@code output}
     * (and never a chat-completions {@code choices} array); a wrong path typically
     * shows up as a top-level {@code error} object instead.
     */
    private String validateResponsesBody(String body) {
        if (body == null || body.isBlank()) {
            return "响应体为空，无法确认是否支持 /responses";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                String message = error.isObject() ? error.path("message").asText("") : error.asText("");
                return "供应商 /responses 返回错误" + (message.isBlank() ? "" : ": " + message);
            }
            boolean looksLikeResponses = root.has("output")
                    || root.has("output_text")
                    || "response".equals(root.path("object").asText(""))
                    || root.has("status");
            if (!looksLikeResponses) {
                return "响应不像 Responses API（缺少 output/status），该端点可能不支持 /responses";
            }
            return null;
        } catch (Exception e) {
            return "/responses 返回的不是合法 JSON：" + e.getMessage();
        }
    }

    private HttpRequest buildHttpRequest(String testUrl, String requestBody, ProviderApiStyle apiStyle, String apiKey,
                                         UpstreamRequestConfig upstreamConfig) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(testUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30));
        upstreamConfig.headers().forEach(requestBuilder::header);

        if (apiStyle == ProviderApiStyle.ANTHROPIC) {
            requestBuilder
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        return requestBuilder.build();
    }

    private boolean shouldRetryOpenRouterWithAvailableProviders(HttpResponse<String> response) {
        if (response.statusCode() != 404 || response.body() == null) {
            return false;
        }
        String body = response.body();
        return body.contains("No allowed providers are available")
                && body.contains("available_providers");
    }

    private List<String> extractOpenRouterAvailableProviders(String responseBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseBody);
            com.fasterxml.jackson.databind.JsonNode providersNode = root.path("error")
                    .path("metadata")
                    .path("available_providers");
            if (!providersNode.isArray()) {
                return List.of();
            }
            List<String> providers = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node : providersNode) {
                String provider = node.asText();
                if (provider != null && !provider.isBlank()) {
                    providers.add(provider.trim().toLowerCase());
                }
            }
            return providers;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalizedUrl = sanitizeText(baseUrl);
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "https://" + normalizedUrl;
        }
        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        return normalizedUrl;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .trim()
                // 移除所有不可见的特殊字符（零宽度空格等控制字符）
                .replaceAll("[\\x00-\\x1F\\x7F-\\x9F\\u200b-\\u200d\\ufeff]", "");
    }

    private String sanitizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        return sanitizeText(value);
    }

    private String sanitizeCode(String value) {
        return sanitizeText(value).toLowerCase();
    }

    private String buildMessagesTestUrl(String normalizedUrl) {
        if (normalizedUrl.endsWith("/v1/messages") || normalizedUrl.endsWith("/messages")) {
            return normalizedUrl;
        }
        if (normalizedUrl.endsWith("/v1")) {
            return normalizedUrl + "/messages";
        }
        return normalizedUrl + "/v1/messages";
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
                .targetTool(provider.getTargetTool() != null ? provider.getTargetTool().name() : null)
                .wireApi(provider.getWireApi())
                .createdAt(provider.getCreatedAt())
                .build();
    }
}
