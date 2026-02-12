package com.paicoding.paiswitch.service.ai;

import com.paicoding.paiswitch.domain.dto.SwitchDto;
import com.paicoding.paiswitch.service.SwitchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class SpringAiConfig {

    public record SwitchModelRequest(Long userId, String providerCode, String clientInfo) {}

    public record SwitchModelResult(boolean success, String message, String currentProvider) {}

    @Bean
    @Description("Switch to a specified AI model provider. Use this when the user wants to change or switch to a different AI model.")
    public Function<SwitchModelRequest, SwitchModelResult> switchModel(SwitchService switchService) {
        return request -> {
            SwitchDto.SwitchResult result = switchService.switchToProvider(
                    request.userId(),
                    request.providerCode(),
                    com.paicoding.paiswitch.domain.enums.SwitchType.AI_NATURAL_LANGUAGE,
                    null,
                    request.clientInfo()
            );
            return new SwitchModelResult(
                    result.getSuccess(),
                    result.getMessage(),
                    result.getCurrentProvider() != null ? result.getCurrentProvider().getName() : null
            );
        };
    }
}
