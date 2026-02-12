package com.paicoding.paiswitch.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.SwitchDto;
import com.paicoding.paiswitch.domain.entity.AiConversation;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.UserConfig;
import com.paicoding.paiswitch.domain.enums.SwitchType;
import com.paicoding.paiswitch.repository.AiConversationRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
import com.paicoding.paiswitch.service.ApiKeyService;
import com.paicoding.paiswitch.service.SwitchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final UserConfigRepository configRepository;
    private final AiConversationRepository conversationRepository;
    private final ApiKeyService apiKeyService;
    private final SwitchService switchService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String SYSTEM_PROMPT = """
            你是 PaiSwitch 的 AI 助手，帮助用户管理和切换 AI 模型。

            你可以：
            1. 帮助用户切换到不同的 AI 模型提供商（如 Claude、DeepSeek、智谱 AI、OpenRouter）
            2. 回答关于各种 AI 模型的问题
            3. 提供模型选择的建议

            当用户要求切换模型时，请使用 switchModel 函数来执行切换。

            可用的模型提供商：
            - claude: Claude (Anthropic 官方)
            - deepseek: DeepSeek V3
            - zhipu: 智谱 AI (GLM-4.5)
            - openrouter: OpenRouter (多模型网关)

            请用中文与用户交流，保持友好和专业的态度。
            """;

    private final Map<String, AnthropicChatModel> chatModelCache = new ConcurrentHashMap<>();
    private static final Pattern FUNCTION_CALL_BLOCK_PATTERN = Pattern.compile("<FunctionCall>(.*?)</FunctionCall>", Pattern.DOTALL);
    private static final Pattern TOOL_CALL_BLOCK_PATTERN = Pattern.compile("\\[TOOL_CALL\\](.*?)\\[/TOOL_CALL\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("tool_name:\\s*([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_NAME_ALT_PATTERN = Pattern.compile("tool\\s*=>\\s*\"?([\\w-]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_ARGS_PATTERN = Pattern.compile("tool_args:\\s*(\\{.*\\})", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOOL_ARGS_ALT_PATTERN = Pattern.compile("args\\s*=>\\s*(\\{.*\\})", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MODEL_FLAG_PATTERN = Pattern.compile("--(?:model|provider|providerCode)\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROVIDER_KEY_VALUE_PATTERN = Pattern.compile("(?:providerCode|provider|model|code|name)\\s*[:=>]\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    @Transactional
    public SwitchDto.NaturalLanguageResponse processNaturalLanguage(Long userId, SwitchDto.NaturalLanguageRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        saveConversation(userId, sessionId, "user", request.getPrompt());

        ParsedSwitchCommand quickSwitchCommand = parseSwitchCommandFromUserPrompt(request.getPrompt());
        if (quickSwitchCommand != null) {
            SwitchDto.SwitchResult switchResult = switchService.switchToProvider(
                    userId,
                    quickSwitchCommand.providerCode(),
                    SwitchType.AI_NATURAL_LANGUAGE,
                    request.getPrompt(),
                    request.getClientInfo()
            );
            String quickResponse = "已收到你的切换请求。\n\n切换结果：" + switchResult.getMessage();
            saveConversation(userId, sessionId, "assistant", quickResponse);
            return SwitchDto.NaturalLanguageResponse.builder()
                    .aiResponse(quickResponse)
                    .switchTriggered(true)
                    .switchResult(switchResult)
                    .sessionId(sessionId)
                    .build();
        }

        UserConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CONFIG_NOT_FOUND));

        ModelProvider provider = config.getCurrentProvider();
        String apiKey = apiKeyService.getDecryptedApiKey(userId, provider.getCode());

        try {
            String aiResponse;
            if (isOpenRouterProvider(provider)) {
                aiResponse = callOpenRouterChat(provider, apiKey, request.getPrompt());
            } else {
                AnthropicChatModel chatModel = getOrCreateChatModel(provider, apiKey);
                List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(SYSTEM_PROMPT));
                messages.add(new UserMessage(request.getPrompt()));
                Prompt prompt = new Prompt(messages);
                ChatResponse response = chatModel.call(prompt);
                aiResponse = response.getResult().getOutput().getContent();
            }

            SwitchExecutionResult switchExecution = executeSwitchFromAiResponse(userId, request, aiResponse);
            String finalAiResponse = switchExecution.aiResponse();

            saveConversation(userId, sessionId, "assistant", finalAiResponse);

            return SwitchDto.NaturalLanguageResponse.builder()
                    .aiResponse(finalAiResponse)
                    .switchTriggered(switchExecution.switchTriggered())
                    .switchResult(switchExecution.switchResult())
                    .sessionId(sessionId)
                    .build();
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage(), e);
            throw new BusinessException(ResponseCode.AI_SERVICE_ERROR, "AI service error: " + e.getMessage());
        }
    }

    private SwitchExecutionResult executeSwitchFromAiResponse(
            Long userId,
            SwitchDto.NaturalLanguageRequest request,
            String aiResponse
    ) {
        ParsedSwitchCommand command = parseSwitchCommand(aiResponse);
        if (command == null) {
            command = parseSwitchCommandFromUserPrompt(request.getPrompt());
        }
        String cleanedResponse = stripFunctionCallBlock(aiResponse).trim();

        if (command == null) {
            String safeResponse = cleanedResponse.isBlank() ? aiResponse : cleanedResponse;
            return new SwitchExecutionResult(safeResponse, false, null);
        }

        SwitchDto.SwitchResult switchResult = switchService.switchToProvider(
                userId,
                command.providerCode(),
                SwitchType.AI_NATURAL_LANGUAGE,
                request.getPrompt(),
                request.getClientInfo()
        );

        String resultLine = "切换结果：" + switchResult.getMessage();
        String finalResponse = cleanedResponse.isBlank()
                ? resultLine
                : cleanedResponse + "\n\n" + resultLine;

        return new SwitchExecutionResult(finalResponse, true, switchResult);
    }

    private ParsedSwitchCommand parseSwitchCommandFromUserPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        if (!isSwitchIntent(prompt)) {
            return null;
        }
        String providerCode = normalizeProviderCode(prompt);
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        return new ParsedSwitchCommand(providerCode);
    }

    private boolean isSwitchIntent(String prompt) {
        String normalized = prompt.toLowerCase(Locale.ROOT);
        return normalized.contains("切换")
                || normalized.contains("换成")
                || normalized.contains("改成")
                || normalized.contains("切到")
                || normalized.contains("switch to")
                || normalized.startsWith("用");
    }

    private ParsedSwitchCommand parseSwitchCommand(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return null;
        }

        ParsedSwitchCommand fromFunctionCall = parseSwitchCommandFromFunctionCall(aiResponse);
        if (fromFunctionCall != null) {
            return fromFunctionCall;
        }
        return parseSwitchCommandFromToolCall(aiResponse);
    }

    private String stripFunctionCallBlock(String text) {
        if (text == null) {
            return "";
        }
        String withoutFunctionCall = FUNCTION_CALL_BLOCK_PATTERN.matcher(text).replaceAll("");
        return TOOL_CALL_BLOCK_PATTERN.matcher(withoutFunctionCall).replaceAll("").trim();
    }

    private ParsedSwitchCommand parseSwitchCommandFromFunctionCall(String aiResponse) {
        Matcher blockMatcher = FUNCTION_CALL_BLOCK_PATTERN.matcher(aiResponse);
        if (!blockMatcher.find()) {
            return null;
        }
        String functionCallBlock = blockMatcher.group(1);
        Matcher toolNameMatcher = TOOL_NAME_PATTERN.matcher(functionCallBlock);
        if (!toolNameMatcher.find()) {
            return null;
        }
        if (!"switchModel".equalsIgnoreCase(toolNameMatcher.group(1))) {
            return null;
        }

        Matcher argsMatcher = TOOL_ARGS_PATTERN.matcher(functionCallBlock);
        if (!argsMatcher.find()) {
            return null;
        }
        String providerCode = parseProviderFromArgs(argsMatcher.group(1));
        return providerCode == null ? null : new ParsedSwitchCommand(providerCode);
    }

    private ParsedSwitchCommand parseSwitchCommandFromToolCall(String aiResponse) {
        Matcher blockMatcher = TOOL_CALL_BLOCK_PATTERN.matcher(aiResponse);
        if (!blockMatcher.find()) {
            return null;
        }
        String toolCallBlock = blockMatcher.group(1);

        Matcher toolNameMatcher = TOOL_NAME_ALT_PATTERN.matcher(toolCallBlock);
        if (!toolNameMatcher.find()) {
            return null;
        }
        if (!"switchModel".equalsIgnoreCase(toolNameMatcher.group(1))) {
            return null;
        }

        Matcher modelFlagMatcher = MODEL_FLAG_PATTERN.matcher(toolCallBlock);
        if (modelFlagMatcher.find()) {
            String providerCode = normalizeProviderCode(modelFlagMatcher.group(1));
            return providerCode == null ? null : new ParsedSwitchCommand(providerCode);
        }

        Matcher argsMatcher = TOOL_ARGS_ALT_PATTERN.matcher(toolCallBlock);
        if (!argsMatcher.find()) {
            return null;
        }
        String providerCode = parseProviderFromArgs(argsMatcher.group(1));
        return providerCode == null ? null : new ParsedSwitchCommand(providerCode);
    }

    private String parseProviderFromArgs(String argsText) {
        if (argsText == null || argsText.isBlank()) {
            return null;
        }
        try {
            JsonNode argsNode = objectMapper.readTree(argsText);
            String rawProvider = firstNonBlank(
                    textValue(argsNode, "providerCode"),
                    textValue(argsNode, "provider"),
                    textValue(argsNode, "model"),
                    textValue(argsNode, "code"),
                    textValue(argsNode, "name")
            );
            return normalizeProviderCode(rawProvider);
        } catch (Exception ignored) {
            Matcher modelFlagMatcher = MODEL_FLAG_PATTERN.matcher(argsText);
            if (modelFlagMatcher.find()) {
                return normalizeProviderCode(modelFlagMatcher.group(1));
            }
            Matcher keyValueMatcher = PROVIDER_KEY_VALUE_PATTERN.matcher(argsText);
            if (keyValueMatcher.find()) {
                return normalizeProviderCode(keyValueMatcher.group(1));
            }
            return null;
        }
    }

    private String textValue(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeProviderCode(String rawProvider) {
        if (rawProvider == null || rawProvider.isBlank()) {
            return null;
        }
        String normalized = rawProvider.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("deepseek")) return "deepseek";
        if (normalized.contains("openrouter")) return "openrouter";
        if (normalized.contains("zhipu") || normalized.contains("智谱") || normalized.contains("glm")) return "zhipu";
        if (normalized.contains("claude") || normalized.contains("anthropic")) return "claude";
        return normalized;
    }

    @Transactional(readOnly = true)
    public SwitchDto.ConversationHistoryResponse getLatestConversation(Long userId) {
        Optional<AiConversation> latest = conversationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (latest.isEmpty()) {
            return SwitchDto.ConversationHistoryResponse.builder()
                    .sessionId(null)
                    .messages(List.of())
                    .build();
        }
        String latestSessionId = latest.get().getSessionId();
        return getConversationHistory(userId, latestSessionId);
    }

    @Transactional(readOnly = true)
    public SwitchDto.ConversationHistoryResponse getConversationHistory(Long userId, String sessionId) {
        List<AiConversation> conversations = conversationRepository.findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId);
        return SwitchDto.ConversationHistoryResponse.builder()
                .sessionId(sessionId)
                .messages(conversations.stream()
                        .map(conversation -> SwitchDto.ConversationMessage.builder()
                                .role(conversation.getRole())
                                .content(conversation.getContent())
                                .createdAt(conversation.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private boolean isOpenRouterProvider(ModelProvider provider) {
        if (provider == null) {
            return false;
        }
        if ("openrouter".equalsIgnoreCase(provider.getCode())) {
            return true;
        }
        return provider.getBaseUrl() != null && provider.getBaseUrl().toLowerCase().contains("openrouter.ai");
    }

    private String callOpenRouterChat(ModelProvider provider, String apiKey, String userPrompt) {
        String requestUrl = buildOpenRouterChatCompletionsUrl(normalizeBaseUrl(provider.getBaseUrl()));
        String normalizedApiKey = normalizeApiKey(apiKey);
        String requestBody = buildOpenRouterChatRequestBody(provider.getModelName(), userPrompt, buildProviderOrderFromModel(provider.getModelName()));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + normalizedApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            log.info("AI OpenRouter request -> url={}, model={}, body={}",
                    requestUrl,
                    provider.getModelName(),
                    singleLine(requestBody));

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("AI OpenRouter response <- status={}, body={}", response.statusCode(), truncate(response.body(), 4000));

            if (shouldRetryWithAvailableProviders(response)) {
                List<String> availableProviders = extractAvailableProviders(response.body());
                if (!availableProviders.isEmpty()) {
                    String retryBody = buildOpenRouterChatRequestBody(provider.getModelName(), userPrompt, availableProviders);
                    HttpRequest retryRequest = HttpRequest.newBuilder()
                            .uri(URI.create(requestUrl))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + normalizedApiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(retryBody))
                            .timeout(Duration.ofSeconds(45))
                            .build();
                    log.info("AI OpenRouter retry -> url={}, order={}, body={}", requestUrl, availableProviders, singleLine(retryBody));
                    response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
                    log.info("AI OpenRouter retry response <- status={}, body={}", response.statusCode(), truncate(response.body(), 4000));
                }
            }

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String content = extractOpenRouterAssistantContent(response.body());
                if (content != null && !content.isBlank()) {
                    return content;
                }
                throw new BusinessException(ResponseCode.AI_SERVICE_ERROR, "AI service error: OpenRouter 返回为空");
            }

            String errorMsg = extractErrorMessage(response.body());
            throw new BusinessException(ResponseCode.AI_SERVICE_ERROR, "AI service error: " + errorMsg);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.AI_SERVICE_ERROR, "AI service error: " + e.getMessage());
        }
    }

    private String buildOpenRouterChatRequestBody(String modelName, String prompt, List<String> providerOrder) {
        String escapedPrompt = escapeJson(prompt);
        String escapedSystem = escapeJson(SYSTEM_PROMPT);
        String escapedModel = escapeJson(modelName);
        if (providerOrder == null || providerOrder.isEmpty()) {
            return """
                {
                  "model": "%s",
                  "max_tokens": 1024,
                  "temperature": 0.7,
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                  ]
                }
                """.formatted(escapedModel, escapedSystem, escapedPrompt);
        }

        String providerOrderJson = providerOrder.stream()
                .map(provider -> "\"" + escapeJson(provider) + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return """
            {
              "model": "%s",
              "max_tokens": 1024,
              "temperature": 0.7,
              "messages": [
                {"role": "system", "content": "%s"},
                {"role": "user", "content": "%s"}
              ],
              "provider": {
                "order": [%s],
                "allow_fallbacks": true
              }
            }
            """.formatted(escapedModel, escapedSystem, escapedPrompt, providerOrderJson);
    }

    private List<String> buildProviderOrderFromModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return List.of();
        }
        int slashIndex = modelName.indexOf('/');
        if (slashIndex <= 0) {
            return List.of();
        }
        String provider = modelName.substring(0, slashIndex).trim().toLowerCase();
        return provider.isEmpty() ? List.of() : List.of(provider);
    }

    private boolean shouldRetryWithAvailableProviders(HttpResponse<String> response) {
        if (response == null || response.statusCode() != 404 || response.body() == null) {
            return false;
        }
        return response.body().contains("No allowed providers are available")
                && response.body().contains("available_providers");
    }

    private List<String> extractAvailableProviders(String body) {
        try {
            JsonNode providersNode = objectMapper.readTree(body)
                    .path("error")
                    .path("metadata")
                    .path("available_providers");
            if (!providersNode.isArray()) {
                return List.of();
            }
            List<String> providers = new ArrayList<>();
            for (JsonNode node : providersNode) {
                String value = node.asText();
                if (value != null && !value.isBlank()) {
                    providers.add(value.trim().toLowerCase());
                }
            }
            return providers;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String extractOpenRouterAssistantContent(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isTextual()) {
                return contentNode.asText();
            }
            if (contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : contentNode) {
                    String text = item.path("text").asText("");
                    if (!text.isBlank()) {
                        if (!sb.isEmpty()) {
                            sb.append('\n');
                        }
                        sb.append(text);
                    }
                }
                return sb.toString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Unknown error";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode msg = root.path("error").path("message");
            if (!msg.isMissingNode() && !msg.isNull()) {
                return msg.asText();
            }
        } catch (Exception ignored) {
        }
        return body.length() > 300 ? body.substring(0, 300) + "...(truncated)" : body;
    }

    private String buildOpenRouterChatCompletionsUrl(String baseUrl) {
        if (baseUrl.endsWith("/v1/chat/completions") || baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/chat/completions";
        }
        return baseUrl + "/v1/chat/completions";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String normalized = apiKey.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return normalized.substring(7).trim();
        }
        return normalized;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String singleLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
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

    private AnthropicChatModel getOrCreateChatModel(ModelProvider provider, String apiKey) {
        String cacheKey = provider.getCode() + "_" + apiKey.hashCode();

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            AnthropicApi anthropicApi = new AnthropicApi(provider.getBaseUrl(), apiKey);

            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .withModel(provider.getModelName())
                    .withMaxTokens(1024)
                    .withTemperature(0.7)
                    .build();

            return new AnthropicChatModel(anthropicApi, options);
        });
    }

    private void saveConversation(Long userId, String sessionId, String role, String content) {
        AiConversation conversation = AiConversation.builder()
                .user(com.paicoding.paiswitch.domain.entity.User.builder().id(userId).build())
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .build();

        conversationRepository.save(conversation);
    }

    public void clearModelCache() {
        chatModelCache.clear();
        log.info("Cleared chat model cache");
    }

    private record ParsedSwitchCommand(String providerCode) {}

    private record SwitchExecutionResult(
            String aiResponse,
            boolean switchTriggered,
            SwitchDto.SwitchResult switchResult
    ) {}
}
