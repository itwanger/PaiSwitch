package com.paicoding.paiswitch.common.config;

import com.paicoding.paiswitch.domain.entity.ModelProvider;
import com.paicoding.paiswitch.domain.entity.User;
import com.paicoding.paiswitch.domain.entity.UserConfig;
import com.paicoding.paiswitch.domain.enums.UserStatus;
import com.paicoding.paiswitch.repository.ModelProviderRepository;
import com.paicoding.paiswitch.repository.UserConfigRepository;
import com.paicoding.paiswitch.repository.UserRepository;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        createDefaultUserIfNotExists();
    }

    private void createDefaultUserIfNotExists() {
        String defaultUsername = "admin";
        String defaultPassword = "admin123";
        String defaultEmail = "admin@paiswitch.local";

        if (!userRepository.existsByUsername(defaultUsername)) {
            User user = User.builder()
                    .username(defaultUsername)
                    .email(defaultEmail)
                    .passwordHash(passwordEncoder.encode(defaultPassword))
                    .nickname("管理员")
                    .status(UserStatus.ACTIVE)
                    .build();

            user = userRepository.save(user);

            ModelProvider defaultProvider = modelProviderRepository.findByCode("claude")
                    .orElseThrow(() -> new IllegalStateException("Default provider not found"));

            UserConfig config = UserConfig.builder()
                    .user(user)
                    .currentProvider(defaultProvider)
                    .apiTimeout(600000)
                    .build();

            userConfigRepository.save(config);

            log.info("Created default user: {} / {}", defaultUsername, defaultPassword);
        }
    }
}
