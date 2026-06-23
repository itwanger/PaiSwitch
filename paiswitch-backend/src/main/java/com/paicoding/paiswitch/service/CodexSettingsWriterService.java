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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes Codex configuration into ~/.codex/auth.json and ~/.codex/config.toml.
 * <p>
 * Three shapes:
 * <ul>
 *   <li><b>OpenAI Official</b> — minimal config, relies on Codex's built-in OpenAI
 *       provider and the existing OAuth login (or PaiSwitch-stored OPENAI_API_KEY).</li>
 *   <li><b>Native Responses (e.g. StepFun)</b> — providers whose
 *       {@code wire_api = "responses"} already speak the Responses API natively, so
 *       {@code base_url} points straight at their real endpoint and the proxy is
 *       bypassed entirely.</li>
 *   <li><b>Third-party chat-completions (DeepSeek / Kimi / Zhipu / Bailian)</b> —
 *       Codex CLI ≥ 0.55 only accepts {@code wire_api = "responses"}. Since these
 *       providers speak chat-completions, we point {@code base_url} at PaiSwitch's
 *       local proxy (controlled by {@code paiswitch.codex.proxy.base-url}, default
 *       {@code http://127.0.0.1:8086}); the proxy does Responses ↔ chat translation.</li>
 *   <li><b>Custom user-created</b> — native or proxied depending on its
 *       {@code wire_api}.</li>
 * </ul>
 * Example for DeepSeek:
 * <pre>
 * model_provider = "deepseek"
 * model = "deepseek-v4-pro"
 * model_reasoning_effort = "high"
 * disable_response_storage = true
 *
 * [model_providers.deepseek]
 * name = "DeepSeek"
 * base_url = "http://127.0.0.1:8086/codex-proxy/deepseek/v1"
 * wire_api = "responses"
 * requires_openai_auth = true
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodexSettingsWriterService {

    private static final String AUTH_PATH_PROPERTY = "paiswitch.codex.auth.path";
    private static final String CONFIG_PATH_PROPERTY = "paiswitch.codex.config.path";
    private static final String DEFAULT_PROVIDER_KEY = "custom";
    private static final String OPENAI_OFFICIAL_CODE = "openai";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiKeyRepository apiKeyRepository;
    private final EncryptionService encryptionService;
    private final com.paicoding.paiswitch.proxy.ProxyEndpointResolver proxyEndpointResolver;

    public void writeToSettings(Long userId, ModelProvider provider) {
        try {
            if (isOpenAiOfficial(provider)) {
                writeAuthJsonForOpenAiOfficial(userId, provider);
                writeConfigTomlForOpenAiOfficial(provider);
            } else {
                writeAuthJson(userId, provider);
                writeConfigToml(provider);
            }
            log.info("Successfully wrote Codex config for provider: {}", provider.getCode());
        } catch (IOException e) {
            log.error("Failed to write Codex settings: {}", e.getMessage());
            throw new RuntimeException("Failed to write Codex settings", e);
        }
    }

    private boolean isOpenAiOfficial(ModelProvider provider) {
        return Boolean.TRUE.equals(provider.getIsBuiltin())
                && OPENAI_OFFICIAL_CODE.equalsIgnoreCase(provider.getCode());
    }

    private void writeAuthJson(Long userId, ModelProvider provider) throws IOException {
        Path path = resolveAuthPath();
        ensureParent(path);

        ObjectNode root = readOrCreateAuthRoot(path);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByUserIdAndProviderId(userId, provider.getId());
        String decryptedKey = apiKeyOpt
                .map(k -> encryptionService.decrypt(k.getEncryptedKey()))
                .orElse("");
        root.put("OPENAI_API_KEY", decryptedKey == null ? "" : decryptedKey);
        // Third-party providers use the API key path. Drop auth_mode so Codex CLI
        // doesn't keep using a stale ChatGPT OAuth session for this request, but
        // keep the `tokens` / `last_refresh` blobs intact so a future switch back
        // to OpenAI Official can re-enter OAuth mode without re-login.
        root.remove("auth_mode");

        backupIfExists(path);
        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Files.writeString(path, jsonContent);
    }

    /**
     * For OpenAI Official:
     * <ul>
     *   <li>If a PaiSwitch API key is stored — write it into {@code OPENAI_API_KEY},
     *       drop {@code auth_mode}, and preserve every other field (including
     *       OAuth {@code tokens}).</li>
     *   <li>Otherwise — restore the OAuth login mode in place: re-set
     *       {@code auth_mode = "chatgpt"} and clear {@code OPENAI_API_KEY} so a
     *       previously-saved {@code tokens} blob takes over. If there are no
     *       tokens either, the file is left untouched and the user must
     *       {@code codex login}.</li>
     * </ul>
     */
    private void writeAuthJsonForOpenAiOfficial(Long userId, ModelProvider provider) throws IOException {
        Path path = resolveAuthPath();
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByUserIdAndProviderId(userId, provider.getId());
        String storedKey = apiKeyOpt
                .map(k -> encryptionService.decrypt(k.getEncryptedKey()))
                .orElse(null);
        boolean hasStoredKey = storedKey != null && !storedKey.isBlank();

        if (hasStoredKey) {
            ensureParent(path);
            ObjectNode root = readOrCreateAuthRoot(path);
            root.put("OPENAI_API_KEY", storedKey);
            root.remove("auth_mode");
            backupIfExists(path);
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return;
        }

        if (!Files.exists(path)) {
            log.info("OpenAI Official: no auth.json and no stored API key; user needs to `codex login`.");
            return;
        }
        ObjectNode root = readOrCreateAuthRoot(path);
        JsonNode tokensNode = root.get("tokens");
        boolean hasOAuthTokens = tokensNode != null && !tokensNode.isNull() && !tokensNode.isMissingNode();
        if (!hasOAuthTokens) {
            log.info("OpenAI Official: no stored API key and no OAuth tokens; user needs to `codex login`.");
            return;
        }

        boolean changed = false;
        if (!"chatgpt".equals(root.path("auth_mode").asText(""))) {
            root.put("auth_mode", "chatgpt");
            changed = true;
        }
        JsonNode keyNode = root.get("OPENAI_API_KEY");
        if (keyNode != null && !keyNode.isNull()) {
            root.putNull("OPENAI_API_KEY");
            changed = true;
        }
        if (!changed) {
            log.info("OpenAI Official: OAuth login state already in place");
            return;
        }
        backupIfExists(path);
        Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        log.info("OpenAI Official: restored OAuth login state in auth.json");
    }

    private ObjectNode readOrCreateAuthRoot(Path path) {
        if (!Files.exists(path)) {
            return objectMapper.createObjectNode();
        }
        try {
            String content = Files.readString(path);
            if (content == null || content.isBlank()) {
                return objectMapper.createObjectNode();
            }
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed instanceof ObjectNode obj) {
                return obj;
            }
            log.warn("auth.json is not a JSON object, recreating");
            return objectMapper.createObjectNode();
        } catch (IOException e) {
            log.warn("Failed to read existing auth.json ({}), recreating", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private void writeConfigToml(ModelProvider provider) throws IOException {
        Path path = resolveConfigPath();
        ensureParent(path);

        String providerKey = (provider.getProviderKey() != null && !provider.getProviderKey().isBlank())
                ? provider.getProviderKey()
                : DEFAULT_PROVIDER_KEY;
        String model = provider.getModelName() == null ? "" : provider.getModelName();
        String displayName = provider.getName() == null ? providerKey : provider.getName();

        // Codex CLI ≥ 0.55 only accepts wire_api = "responses".
        //   - Providers that natively speak the Responses API (wire_api = "responses",
        //     e.g. StepFun) are pointed straight at their real base_url; no proxy.
        //   - Providers that only speak chat-completions (DeepSeek / Kimi / ...) are
        //     routed through the local PaiSwitch proxy which does the Responses ↔ chat
        //     translation.
        // Either way Codex sees wire_api = "responses".
        String baseUrl = speaksResponsesNatively(provider)
                ? trimTrailingSlash(provider.getBaseUrl())
                : proxyEndpointResolver.getProxyBaseUrl() + "/codex-proxy/" + provider.getCode() + "/v1";

        String providerBlock = """
                [model_providers.%s]
                name = %s
                base_url = %s
                wire_api = "responses"
                requires_openai_auth = true
                """.formatted(providerKey, tomlString(displayName), tomlString(baseUrl));

        backupIfExists(path);
        Files.writeString(path, updateConfigToml(path, List.of(
                "model_provider = " + tomlString(providerKey),
                "model = " + tomlString(model),
                "model_reasoning_effort = \"high\"",
                "disable_response_storage = true"
        ), providerKey, providerBlock));
    }


    /**
     * For OpenAI Official, write a minimal config that relies on Codex's built-in
     * OpenAI provider. No `model_provider`, no `[model_providers.openai]` block,
     * no `disable_response_storage` (those are for third-party providers).
     */
    private void writeConfigTomlForOpenAiOfficial(ModelProvider provider) throws IOException {
        Path path = resolveConfigPath();
        ensureParent(path);

        String model = provider.getModelName() == null ? "" : provider.getModelName();

        backupIfExists(path);
        Files.writeString(path, updateConfigToml(path, List.of(
                "model = " + tomlString(model),
                "model_reasoning_effort = \"high\""
        ), null, null));
    }

    private String updateConfigToml(Path path, List<String> modelLines, String providerKey, String providerBlock) throws IOException {
        String existing = Files.exists(path) ? Files.readString(path) : "";
        existing = existing.replace("\r\n", "\n").replace('\r', '\n');

        TomlParts parts = splitTopLevel(existing);
        List<String> preservedTopLevel = removeManagedTopLevelKeys(parts.topLevelLines());
        String body = removeProviderBlock(parts.body(), providerKey);

        StringBuilder out = new StringBuilder();
        appendLines(out, modelLines);
        if (!preservedTopLevel.isEmpty()) {
            out.append('\n');
            appendLines(out, preservedTopLevel);
        }
        if (!body.isBlank()) {
            if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append('\n');
            out.append(body.stripLeading());
            if (out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
        }
        if (providerBlock != null && !providerBlock.isBlank()) {
            if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append('\n').append(providerBlock);
            if (out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private TomlParts splitTopLevel(String content) {
        String[] lines = content.split("\n", -1);
        List<String> topLevel = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        boolean inBody = false;
        for (String line : lines) {
            if (!inBody && line.trim().startsWith("[")) {
                inBody = true;
            }
            if (inBody) {
                body.append(line).append('\n');
            } else {
                topLevel.add(line);
            }
        }
        return new TomlParts(topLevel, body.toString());
    }

    private List<String> removeManagedTopLevelKeys(List<String> lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("model =")
                    || trimmed.startsWith("model_provider =")
                    || trimmed.startsWith("model_reasoning_effort =")
                    || trimmed.startsWith("disable_response_storage =")) {
                continue;
            }
            kept.add(line);
        }
        while (!kept.isEmpty() && kept.get(0).isBlank()) {
            kept.remove(0);
        }
        while (!kept.isEmpty() && kept.get(kept.size() - 1).isBlank()) {
            kept.remove(kept.size() - 1);
        }
        return kept;
    }

    private String removeProviderBlock(String body, String providerKey) {
        if (providerKey == null || providerKey.isBlank() || body.isBlank()) {
            return body;
        }
        String header = "[model_providers." + providerKey + "]";
        String[] lines = body.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean skipping = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(header)) {
                skipping = true;
                continue;
            }
            if (skipping && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                skipping = false;
            }
            if (!skipping) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private void appendLines(StringBuilder out, List<String> lines) {
        for (String line : lines) {
            out.append(line).append('\n');
        }
    }

    private record TomlParts(List<String> topLevelLines, String body) {}

    /**
     * True when the upstream natively speaks the Responses API
     * ({@code wire_api = "responses"}), so Codex can call it directly without the
     * local Responses ↔ chat translation proxy.
     */
    private boolean speaksResponsesNatively(ModelProvider provider) {
        String wireApi = provider.getWireApi();
        return wireApi != null && "responses".equalsIgnoreCase(wireApi.trim());
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String tomlString(String value) {
        if (value == null) return "\"\"";
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + escaped + "\"";
    }

    private Path resolveAuthPath() {
        String override = System.getProperty(AUTH_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".codex", "auth.json");
    }

    private Path resolveConfigPath() {
        String override = System.getProperty(CONFIG_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".codex", "config.toml");
    }

    private void ensureParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private void backupIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            String timestamp = Instant.now().toString().replace(":", "-");
            Path backupPath = Paths.get(path.toString() + ".backup." + timestamp);
            Files.copy(path, backupPath);
        }
    }
}
