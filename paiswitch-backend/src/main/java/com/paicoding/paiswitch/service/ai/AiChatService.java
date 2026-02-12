package com.paicoding.paiswitch.service.ai;

import com.paicoding.paiswitch.common.exception.BusinessException;
import com.paicoding.paiswitch.common.response.ResponseCode;
import com.paicoding.paiswitch.domain.dto.SwitchDto;
import com.paicoding.paiswitch.domain.entity.AiConversation;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.UserConfig;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final UserConfigRepository configRepository;
    private final AiConversationRepository conversationRepository;
    private final ApiKeyService apiKeyService;
    private final SwitchService switchService;

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

    @Transactional
    public SwitchDto.NaturalLanguageResponse processNaturalLanguage(Long userId, SwitchDto.NaturalLanguageRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        UserConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CONFIG_NOT_FOUND));

        ModelProvider provider = config.getCurrentProvider();
        String apiKey = apiKeyService.getDecryptedApiKey(userId, provider.getCode());

        AnthropicChatModel chatModel = getOrCreateChatModel(provider, apiKey);

        saveConversation(userId, sessionId, "user", request.getPrompt());

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(request.getPrompt()));

        Prompt prompt = new Prompt(messages);

        try {
            ChatResponse response = chatModel.call(prompt);
            String aiResponse = response.getResult().getOutput().getContent();

            saveConversation(userId, sessionId, "assistant", aiResponse);

            return SwitchDto.NaturalLanguageResponse.builder()
                    .aiResponse(aiResponse)
                    .sessionId(sessionId)
                    .build();
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage(), e);
            throw new BusinessException(ResponseCode.AI_SERVICE_ERROR, "AI service error: " + e.getMessage());
        }
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
}
