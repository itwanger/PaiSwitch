package com.paicoding.paiswitch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.proxy.ProxyEndpointResolver;
import com.paicoding.paiswitch.repository.ApiKeyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodexSettingsWriterServiceTest {

    private static final String AUTH_PATH_PROPERTY = "paiswitch.codex.auth.path";
    private static final String CONFIG_PATH_PROPERTY = "paiswitch.codex.config.path";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty(AUTH_PATH_PROPERTY);
        System.clearProperty(CONFIG_PATH_PROPERTY);
    }

    @Test
    void shouldPreserveExistingCodexConfigWhenWritingThirdPartyProvider() throws Exception {
        Path authPath = tempDir.resolve("auth.json");
        Path configPath = tempDir.resolve("config.toml");
        System.setProperty(AUTH_PATH_PROPERTY, authPath.toString());
        System.setProperty(CONFIG_PATH_PROPERTY, configPath.toString());

        Files.writeString(authPath, """
                {
                  "auth_mode": "chatgpt",
                  "tokens": {"id_token": "keep-me"}
                }
                """);
        Files.writeString(configPath, """
                model = "gpt-5.5"
                notify = ["/bin/echo", "done"]

                [mcp_servers.node_repl]
                command = "/Applications/Codex.app/Contents/Resources/node_repl"

                [projects."/tmp/demo"]
                trust_level = "trusted"
                """);

        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        ProxyEndpointResolver proxyEndpointResolver = mock(ProxyEndpointResolver.class);
        CodexSettingsWriterService service = new CodexSettingsWriterService(
                apiKeyRepository,
                encryptionService,
                proxyEndpointResolver
        );
        ModelProvider provider = ModelProvider.builder()
                .id(20L)
                .code("deepseek")
                .name("DeepSeek")
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-v4-pro")
                .providerKey("deepseek")
                .isBuiltin(true)
                .build();
        ApiKey apiKey = ApiKey.builder().encryptedKey("encrypted").build();

        when(apiKeyRepository.findByUserIdAndProviderId(1L, 20L)).thenReturn(Optional.of(apiKey));
        when(encryptionService.decrypt("encrypted")).thenReturn("sk-test");
        when(proxyEndpointResolver.getProxyBaseUrl()).thenReturn("http://127.0.0.1:54321");

        service.writeToSettings(1L, provider);

        String toml = Files.readString(configPath);
        assertTrue(toml.contains("model_provider = \"deepseek\""));
        assertTrue(toml.contains("model = \"deepseek-v4-pro\""));
        assertTrue(toml.contains("base_url = \"http://127.0.0.1:54321/codex-proxy/deepseek/v1\""));
        assertTrue(toml.contains("notify = [\"/bin/echo\", \"done\"]"));
        assertTrue(toml.contains("[mcp_servers.node_repl]"));
        assertTrue(toml.contains("[projects.\"/tmp/demo\"]"));

        JsonNode auth = objectMapper.readTree(Files.readString(authPath));
        assertFalse(auth.has("auth_mode"));
        assertTrue(auth.has("tokens"));
        assertTrue("sk-test".equals(auth.path("OPENAI_API_KEY").asText()));
    }

    @Test
    void shouldPointDirectlyAtUpstreamForNativeResponsesProvider() throws Exception {
        Path authPath = tempDir.resolve("auth.json");
        Path configPath = tempDir.resolve("config.toml");
        System.setProperty(AUTH_PATH_PROPERTY, authPath.toString());
        System.setProperty(CONFIG_PATH_PROPERTY, configPath.toString());

        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        ProxyEndpointResolver proxyEndpointResolver = mock(ProxyEndpointResolver.class);
        CodexSettingsWriterService service = new CodexSettingsWriterService(
                apiKeyRepository,
                encryptionService,
                proxyEndpointResolver
        );
        ModelProvider provider = ModelProvider.builder()
                .id(30L)
                .code("stepfun")
                .name("StepFun (阶跃星辰)")
                .baseUrl("https://api.stepfun.com/v1")
                .modelName("step-3.7-flash")
                .wireApi("responses")
                .providerKey("stepfun")
                .isBuiltin(true)
                .build();
        ApiKey apiKey = ApiKey.builder().encryptedKey("encrypted").build();

        when(apiKeyRepository.findByUserIdAndProviderId(1L, 30L)).thenReturn(Optional.of(apiKey));
        when(encryptionService.decrypt("encrypted")).thenReturn("sk-step");

        service.writeToSettings(1L, provider);

        String toml = Files.readString(configPath);
        assertTrue(toml.contains("model_provider = \"stepfun\""));
        assertTrue(toml.contains("model = \"step-3.7-flash\""));
        assertTrue(toml.contains("base_url = \"https://api.stepfun.com/v1\""));
        assertTrue(toml.contains("wire_api = \"responses\""));
        // Native responses provider must NOT be routed through the local proxy.
        assertFalse(toml.contains("/codex-proxy/"));

        JsonNode auth = objectMapper.readTree(Files.readString(authPath));
        assertTrue("sk-step".equals(auth.path("OPENAI_API_KEY").asText()));
    }

    @Test
    void shouldSwitchOpenAiOfficialWithoutDeletingUnrelatedCodexConfig() throws Exception {
        Path authPath = tempDir.resolve("auth.json");
        Path configPath = tempDir.resolve("config.toml");
        System.setProperty(AUTH_PATH_PROPERTY, authPath.toString());
        System.setProperty(CONFIG_PATH_PROPERTY, configPath.toString());

        Files.writeString(authPath, """
                {
                  "OPENAI_API_KEY": "old-third-party-key",
                  "tokens": {"id_token": "keep-me"}
                }
                """);
        Files.writeString(configPath, """
                model_provider = "deepseek"
                model = "deepseek-v4-pro"
                disable_response_storage = true
                notify = ["/bin/echo", "done"]

                [model_providers.deepseek]
                name = "DeepSeek"
                base_url = "http://127.0.0.1:8086/codex-proxy/deepseek/v1"
                wire_api = "responses"
                requires_openai_auth = true

                [mcp_servers.node_repl]
                command = "/Applications/Codex.app/Contents/Resources/node_repl"
                """);

        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        ProxyEndpointResolver proxyEndpointResolver = mock(ProxyEndpointResolver.class);
        CodexSettingsWriterService service = new CodexSettingsWriterService(
                apiKeyRepository,
                encryptionService,
                proxyEndpointResolver
        );
        ModelProvider provider = ModelProvider.builder()
                .id(10L)
                .code("openai")
                .name("OpenAI Official")
                .baseUrl("https://api.openai.com/v1")
                .modelName("gpt-5.5")
                .providerKey("openai")
                .isBuiltin(true)
                .build();

        when(apiKeyRepository.findByUserIdAndProviderId(1L, 10L)).thenReturn(Optional.empty());

        service.writeToSettings(1L, provider);

        String toml = Files.readString(configPath);
        assertTrue(toml.contains("model = \"gpt-5.5\""));
        assertFalse(toml.contains("model_provider ="));
        assertFalse(toml.contains("disable_response_storage ="));
        assertTrue(toml.contains("notify = [\"/bin/echo\", \"done\"]"));
        assertTrue(toml.contains("[model_providers.deepseek]"));
        assertTrue(toml.contains("[mcp_servers.node_repl]"));

        JsonNode auth = objectMapper.readTree(Files.readString(authPath));
        assertTrue("chatgpt".equals(auth.path("auth_mode").asText()));
        assertTrue(auth.has("OPENAI_API_KEY"));
        assertNull(auth.get("OPENAI_API_KEY").textValue());
        assertTrue(auth.has("tokens"));
    }
}
