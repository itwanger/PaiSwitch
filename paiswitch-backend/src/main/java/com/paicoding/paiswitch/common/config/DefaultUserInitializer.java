package com.paicoding.paiswitch.common.config;

import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.domain.entity.UserConfig;
import com.paicoding.paiswitch.domain.enums.TargetTool;
import com.paicoding.paiswitch.domain.enums.UserStatus;
import com.paicoding.paiswitch.repository.ApiKeyRepository;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
import com.paicoding.paiswitch.repository.UserRepository;
import com.paicoding.paiswitch.service.EncryptionService;
import com.paicoding.paiswitch.service.LocalConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUserInitializer implements CommandLineRunner {

    private static final String CLAUDE_BASE_URL = "https://api.anthropic.com";

    private final UserRepository userRepository;
    private final UserConfigRepository userConfigRepository;
    private final ModelProviderRepository modelProviderRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final EncryptionService encryptionService;
    private final LocalConfigService localConfigService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        ensureBuiltinProvidersExist();
        // Sync settings.json to database before creating user
        syncLocalConfigToDatabase();
        createDefaultUserIfNotExists();
    }

    private void ensureBuiltinProvidersExist() {
        createBuiltinProviderIfMissing("claude", "Claude (Official)",
                "Anthropic Claude official API",
                CLAUDE_BASE_URL,
                "claude-sonnet-4-20250514",
                "claude-3-5-haiku-latest",
                1);
        createBuiltinProviderIfMissing("deepseek", "DeepSeek",
                "DeepSeek AI model with Anthropic compatible API",
                "https://api.deepseek.com/anthropic",
                "deepseek-chat",
                null,
                2);
        createBuiltinProviderIfMissing("zhipu", "Zhipu AI",
                "Zhipu GLM model with Anthropic compatible API",
                "https://open.bigmodel.cn/api/anthropic",
                "glm-4.5",
                "glm-4.5-air",
                3);
        createBuiltinProviderIfMissing("openrouter", "OpenRouter",
                "OpenRouter multi-model gateway",
                "https://openrouter.ai/api",
                "anthropic/claude-sonnet-4",
                "anthropic/claude-3-haiku",
                4);
        createCodexBuiltinProviderIfMissing("openai", "OpenAI Official",
                "OpenAI Codex official endpoint",
                "https://api.openai.com/v1",
                "gpt-5.5",
                null,
                "responses",
                "openai",
                1);
        createCodexBuiltinProviderIfMissing("deepseek", "DeepSeek",
                "DeepSeek API via OpenAI chat-completions",
                "https://api.deepseek.com",
                "deepseek-v4-pro",
                "deepseek-v4-flash",
                "chat",
                "deepseek",
                2);
        createCodexBuiltinProviderIfMissing("zhipu", "Zhipu GLM",
                "Zhipu GLM via OpenAI chat-completions",
                "https://open.bigmodel.cn/api/paas/v4",
                "glm-5",
                null,
                "chat",
                "zhipu_glm",
                3);
        createCodexBuiltinProviderIfMissing("kimi", "Kimi (Moonshot)",
                "Moonshot Kimi via OpenAI chat-completions",
                "https://api.moonshot.cn/v1",
                "kimi-k2.6",
                null,
                "chat",
                "kimi",
                4);
        createCodexBuiltinProviderIfMissing("qwen", "Qwen (Bailian)",
                "Aliyun Bailian Qwen-Coder via OpenAI chat-completions",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen3-coder-plus",
                "qwen3-max",
                "chat",
                "bailian",
                5);
        createCodexBuiltinProviderIfMissing("skyclaw", "SkyClaw v1.0",
                "Skywork SkyClaw agent model via OpenAI-compatible chat-completions",
                "https://api.apifree.ai/v1",
                "skywork-ai/skyclaw-v1",
                "skywork-ai/skyclaw-v1-lite",
                "chat",
                "skyclaw",
                6);
        createCodexBuiltinProviderIfMissing("xfyun-maas", "iFlytek Spark MaaS",
                "讯飞星辰 MaaS OpenAI-compatible chat-completions",
                "https://maas-api.cn-huabei-1.xf-yun.com/v2",
                "2615338780587020",
                null,
                "chat",
                "xfyun_maas",
                7);
        // StepFun natively speaks the Responses API, so Codex connects directly to
        // the real base_url (no local proxy). The settings writer treats
        // wire_api='responses' as a native passthrough.
        createCodexBuiltinProviderIfMissing("stepfun", "StepFun (阶跃星辰)",
                "阶跃 Step 模型,原生 Responses API,Codex 直连无需代理",
                "https://api.stepfun.com/v1",
                "step-3.7-flash",
                null,
                "responses",
                "stepfun",
                8);
        // Claude Code side: SkyClaw only exposes OpenAI chat-completions. base_url
        // points at the upstream; wire_api='openai' makes the settings writer route
        // ANTHROPIC_BASE_URL through the local /claude-proxy/{code} translator so
        // Claude Code's Anthropic Messages requests get converted both ways.
        createBuiltinProviderWithWireApi("skyclaw", "SkyClaw v1.0",
                "Skywork SkyClaw agent model (auto-translated via local Claude proxy)",
                "https://api.apifree.ai/agent/v1",
                "skywork-ai/skyclaw-v1",
                "skywork-ai/skyclaw-v1-lite",
                "openai",
                5);
        createBuiltinProviderWithWireApi("xfyun-maas", "iFlytek Spark MaaS",
                "讯飞星辰 MaaS OpenAI-compatible inference via local Claude proxy",
                "https://maas-api.cn-huabei-1.xf-yun.com/v2",
                "2615338780587020",
                null,
                "openai",
                6);
    }

    private void createBuiltinProviderIfMissing(String code, String name, String description, String baseUrl,
                                                String modelName, String modelNameSmall, int sortOrder) {
        createBuiltinProviderWithWireApi(code, name, description, baseUrl, modelName, modelNameSmall, null, sortOrder);
    }

    private void createBuiltinProviderWithWireApi(String code, String name, String description, String baseUrl,
                                                  String modelName, String modelNameSmall, String wireApi, int sortOrder) {
        if (modelProviderRepository.existsByCodeAndTargetTool(code, TargetTool.CLAUDE_CODE)) {
            return;
        }

        modelProviderRepository.save(ModelProvider.builder()
                .code(code)
                .name(name)
                .description(description)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .modelNameSmall(modelNameSmall)
                .isBuiltin(true)
                .isActive(true)
                .sortOrder(sortOrder)
                .targetTool(TargetTool.CLAUDE_CODE)
                .wireApi(wireApi)
                .build());
        log.info("Created missing built-in provider: {} (wire_api={})", code, wireApi);
    }

    private void createCodexBuiltinProviderIfMissing(String code, String name, String description, String baseUrl,
                                                     String modelName, String modelNameSmall, String wireApi,
                                                     String providerKey, int sortOrder) {
        if (modelProviderRepository.existsByCodeAndTargetTool(code, TargetTool.CODEX)) {
            return;
        }

        modelProviderRepository.save(ModelProvider.builder()
                .code(code)
                .name(name)
                .description(description)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .modelNameSmall(modelNameSmall)
                .isBuiltin(true)
                .isActive(true)
                .sortOrder(sortOrder)
                .targetTool(TargetTool.CODEX)
                .wireApi(wireApi)
                .providerKey(providerKey)
                .build());
        log.info("Created missing Codex built-in provider: {}", code);
    }

    private void syncLocalConfigToDatabase() {
        try {
            localConfigService.syncLocalConfigToDatabase();
        } catch (Exception e) {
            log.warn("Failed to sync local config to database: {}", e.getMessage());
        }
    }

    private void createDefaultUserIfNotExists() {
        String defaultUsername = "admin";
        String defaultPassword = "admin123";
        String defaultEmail = "admin@paiswitch.local";

        if (!userRepository.existsByUsername(defaultUsername)) {
            // Create user
            User user = User.builder()
                    .username(defaultUsername)
                    .email(defaultEmail)
                    .passwordHash(passwordEncoder.encode(defaultPassword))
                    .nickname("管理员")
                    .status(UserStatus.ACTIVE)
                    .build();

            user = userRepository.save(user);

            // Read local config to determine current provider
            LocalConfigService.LocalConfig localConfig = localConfigService.readLocalConfig();
            String providerCode = localConfig.providerCode();

            ModelProvider defaultProvider = modelProviderRepository.findByCodeAndTargetTool(providerCode, TargetTool.CLAUDE_CODE)
                    .orElseGet(() -> modelProviderRepository.findByCodeAndTargetTool("claude", TargetTool.CLAUDE_CODE)
                            .orElseThrow(() -> new IllegalStateException("Default provider not found")));

            // Create user config with local settings
            UserConfig config = UserConfig.builder()
                    .user(user)
                    .currentProvider(defaultProvider)
                    .apiTimeout(localConfig.apiTimeout())
                    .build();

            userConfigRepository.save(config);

            // Save API key from local config if exists
            if (localConfig.apiKey() != null && !localConfig.apiKey().isEmpty()) {
                String encryptedKey = encryptionService.encrypt(localConfig.apiKey());
                String keyHint = encryptionService.getKeyHint(localConfig.apiKey());

                ApiKey apiKey = ApiKey.builder()
                        .user(user)
                        .provider(defaultProvider)
                        .encryptedKey(encryptedKey)
                        .keyHint(keyHint)
                        .isValid(true)
                        .build();

                apiKeyRepository.save(apiKey);
                log.info("Imported API key from local config for provider: {}", providerCode);
            }

            log.info("Created default user: {} / {} with provider: {}", defaultUsername, defaultPassword, providerCode);
        }
    }
}
