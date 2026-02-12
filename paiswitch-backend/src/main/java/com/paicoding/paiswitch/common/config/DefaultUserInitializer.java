package com.paicoding.paiswitch.common.config;

import com.paicoding.paiswitch.domain.entity.ApiKey;
import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.domain.entity.UserConfig;
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
        // Sync settings.json to database before creating user
        syncLocalConfigToDatabase();
        createDefaultUserIfNotExists();
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

            ModelProvider defaultProvider = modelProviderRepository.findByCode(providerCode)
                    .orElseGet(() -> modelProviderRepository.findByCode("claude")
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
