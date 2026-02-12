package com.paicoding.paiswitch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalConfigService {

    private static final String CONFIG_PATH = System.getProperty("user.home") + "/.claude/settings.json";

    private final ModelProviderRepository providerRepository;
    private static final Map<String, String> BASE_URL_TO_PROVIDER = Map.of(
            "api.anthropic.com", "claude",
            "api.deepseek.com", "deepseek",
            "open.bigmodel.cn", "zhipu",
            "openrouter.ai", "openrouter"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sync settings.json configuration to database.
     * Updates the provider's baseUrl, modelName, and modelNameSmall in the database.
     */
    @Transactional
    public void syncLocalConfigToDatabase() {
        LocalConfig localConfig = readLocalConfig();
        String providerCode = localConfig.providerCode();

        providerRepository.findByCode(providerCode).ifPresent(provider -> {
            boolean updated = false;

            if (localConfig.baseUrl() != null && !localConfig.baseUrl().isEmpty()) {
                if (!localConfig.baseUrl().equals(provider.getBaseUrl())) {
                    provider.setBaseUrl(localConfig.baseUrl());
                    updated = true;
                }
            }

            if (localConfig.model() != null && !localConfig.model().isEmpty()) {
                if (!localConfig.model().equals(provider.getModelName())) {
                    provider.setModelName(localConfig.model());
                    updated = true;
                }
            }

            if (localConfig.smallModel() != null && !localConfig.smallModel().isEmpty()) {
                if (!localConfig.smallModel().equals(provider.getModelNameSmall())) {
                    provider.setModelNameSmall(localConfig.smallModel());
                    updated = true;
                }
            }

            if (updated) {
                providerRepository.save(provider);
                log.info("Synced local config to database for provider: {}, model: {}", providerCode, localConfig.model());
            } else {
                log.info("Local config already in sync with database for provider: {}", providerCode);
            }
        });
    }

    public LocalConfig readLocalConfig() {
        try {
            Path path = Paths.get(CONFIG_PATH);
            if (!Files.exists(path)) {
                log.warn("Local config file not found: {}", CONFIG_PATH);
                return new LocalConfig("claude", null, null, 600000);
            }

            String content = Files.readString(path);
            JsonNode root = objectMapper.readTree(content);
            JsonNode env = root.get("env");

            if (env == null) {
                return new LocalConfig("claude", null, null, 600000);
            }

            String baseUrl = getText(env, "ANTHROPIC_BASE_URL", "");
            String apiKey = getText(env, "ANTHROPIC_API_KEY", "");
            String authToken = getText(env, "ANTHROPIC_AUTH_TOKEN", "");
            String model = getText(env, "ANTHROPIC_MODEL", "");
            String smallModel = getText(env, "ANTHROPIC_SMALL_FAST_MODEL", "");
            int timeout = getInt(env, "API_TIMEOUT_MS", 600000);

            String providerCode = detectProvider(baseUrl);
            String effectiveApiKey = apiKey.isEmpty() ? authToken : apiKey;

            log.info("Read local config: provider={}, model={}", providerCode, model);

            return new LocalConfig(providerCode, model.isEmpty() ? null : model,
                    smallModel.isEmpty() ? null : smallModel, timeout, effectiveApiKey, baseUrl);

        } catch (Exception e) {
            log.error("Failed to read local config: {}", e.getMessage());
            return new LocalConfig("claude", null, null, 600000);
        }
    }

    private String detectProvider(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "claude";
        }

        String lowerUrl = baseUrl.toLowerCase();
        for (Map.Entry<String, String> entry : BASE_URL_TO_PROVIDER.entrySet()) {
            if (lowerUrl.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "claude";
    }

    private String getText(JsonNode node, String key, String defaultValue) {
        JsonNode value = node.get(key);
        return value != null ? value.asText(defaultValue) : defaultValue;
    }

    private int getInt(JsonNode node, String key, int defaultValue) {
        JsonNode value = node.get(key);
        if (value != null && value.isNumber()) {
            return value.asInt();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public record LocalConfig(
            String providerCode,
            String model,
            String smallModel,
            int apiTimeout,
            String apiKey,
            String baseUrl
    ) {
        public LocalConfig(String providerCode, String model, String smallModel, int apiTimeout) {
            this(providerCode, model, smallModel, apiTimeout, null, null);
        }
    }
}
