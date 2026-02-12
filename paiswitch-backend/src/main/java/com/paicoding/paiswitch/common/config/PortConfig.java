package com.paicoding.paiswitch.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class PortConfig {

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final AtomicInteger actualPort = new AtomicInteger(DEFAULT_PORT);

    @Bean
    public TomcatConnectorCustomizer tomcatConnectorCustomizer() {
        return connector -> {
            int port = findAvailablePort(DEFAULT_PORT);
            actualPort.set(port);
            connector.setPort(port);
            log.info("Tomcat will use port: {}", port);
        };
    }

    @Bean
    public ApplicationListener<ServletWebServerInitializedEvent> portLogger() {
        return event -> {
            int port = event.getWebServer().getPort();
            log.info("========================================");
            log.info("PaiSwitch Backend started successfully");
            log.info("Swagger UI: http://localhost:{}/swagger-ui.html", port);
            log.info("API Docs:   http://localhost:{}/api-docs", port);
            log.info("========================================");
        };
    }

    private int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + MAX_PORT_ATTEMPTS; port++) {
            if (isPortAvailable(port)) {
                if (port != DEFAULT_PORT) {
                    log.info("Port {} is in use, switching to port {}", DEFAULT_PORT, port);
                }
                return port;
            }
        }
        // 如果常用端口都被占用，使用随机端口
        try (ServerSocket socket = new ServerSocket(0)) {
            int randomPort = socket.getLocalPort();
            log.info("All ports from {} to {} are in use, using random port: {}",
                    DEFAULT_PORT, DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1, randomPort);
            return randomPort;
        } catch (Exception e) {
            return DEFAULT_PORT;
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
