package com.paicoding.paiswitch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

/**
 * Service for writing configuration to Claude Code settings.json file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsWriterService {

    private static final String CONFIG_PATH = System.getProperty("user.home") + "/.claude/settings.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiKeyRepository apiKeyRepository;
    private final EncryptionService encryptionService;

    /**
     * Write provider configuration to settings.json.
     *
     * @param userId the user ID
     * @param provider the provider to switch to
     */
    public void writeToSettings(Long userId, ModelProvider provider) {
        try {
            Path path = Paths.get(CONFIG_PATH);

            // Read existing config or create new
            ObjectNode root;
            if (Files.exists(path)) {
                String content = Files.readString(path);
                JsonNode parsed = objectMapper.readTree(content);
                root = (ObjectNode) parsed;
            } else {
                root = objectMapper.createObjectNode();
            }

            // Get or create env object
            ObjectNode env = (ObjectNode) root.get("env");
            if (env == null) {
                env = objectMapper.createObjectNode();
                root.set("env", env);
            }

            // Clear previous provider-specific env vars
            clearProviderEnvVars(env);

            // Set new provider configuration
            String providerCode = provider.getCode();

            if ("claude".equals(providerCode)) {
                // For Claude official, just remove third-party env vars
                log.info("Writing Claude official config - removing third-party env vars");
            } else {
                // Set base URL for third-party providers
                env.put("ANTHROPIC_BASE_URL", provider.getBaseUrl());

                // Set model names
                if (provider.getModelName() != null) {
                    env.put("ANTHROPIC_MODEL", provider.getModelName());
                }
                if (provider.getModelNameSmall() != null) {
                    env.put("ANTHROPIC_SMALL_FAST_MODEL", provider.getModelNameSmall());
                }

                // Get API key for this provider
                Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByUserIdAndProviderId(userId, provider.getId());
                if (apiKeyOpt.isPresent()) {
                    String decryptedKey = encryptionService.decrypt(apiKeyOpt.get().getEncryptedKey());
                    env.put("ANTHROPIC_AUTH_TOKEN", decryptedKey);
                }
            }

            // Set API timeout
            env.put("API_TIMEOUT_MS", 600000);

            // Create backup before writing
            createBackup(path);

            // Write new config
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(path, jsonContent);

            log.info("Successfully wrote settings.json for provider: {}", providerCode);

        } catch (IOException e) {
            log.error("Failed to write settings.json: {}", e.getMessage());
            throw new RuntimeException("Failed to write settings.json", e);
        }
    }

    private void clearProviderEnvVars(ObjectNode env) {
        env.remove("ANTHROPIC_BASE_URL");
        env.remove("ANTHROPIC_API_KEY");
        env.remove("ANTHROPIC_AUTH_TOKEN");
        env.remove("ANTHROPIC_MODEL");
        env.remove("ANTHROPIC_SMALL_FAST_MODEL");
    }

    private void createBackup(Path path) throws IOException {
        if (Files.exists(path)) {
            String timestamp = Instant.now().toString().replace(":", "-");
            Path backupPath = Paths.get(path.toString() + ".backup." + timestamp);
            Files.copy(path, backupPath);
            log.info("Created backup: {}", backupPath);
        }
    }
}
