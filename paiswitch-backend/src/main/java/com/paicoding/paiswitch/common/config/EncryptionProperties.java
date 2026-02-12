package com.paicoding.paiswitch.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "encryption")
public class EncryptionProperties {

    private String aesKey;
}
