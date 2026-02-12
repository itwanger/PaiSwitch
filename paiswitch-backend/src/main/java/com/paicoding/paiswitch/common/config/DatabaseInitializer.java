package com.paicoding.paiswitch.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Slf4j
public class DatabaseInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static volatile boolean initialized = false;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (initialized) {
            return;
        }

        ConfigurableEnvironment env = event.getEnvironment();

        String url = env.getProperty("spring.datasource.url");
        String username = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");

        if (url == null || username == null) {
            return;
        }

        String databaseName = extractDatabaseName(url);
        String serverUrl = extractServerUrl(url);

        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `" + databaseName + "` " +
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );

            log.info("Database '{}' is ready", databaseName);
            initialized = true;

        } catch (Exception e) {
            log.warn("Could not auto-create database '{}': {}", databaseName, e.getMessage());
        }
    }

    private String extractDatabaseName(String url) {
        // jdbc:mysql://localhost:3306/paiswitch?params
        int hostEnd = url.indexOf('/', url.indexOf("//") + 2);
        int questionMark = url.indexOf('?', hostEnd);
        if (questionMark > 0) {
            return url.substring(hostEnd + 1, questionMark);
        }
        return url.substring(hostEnd + 1);
    }

    private String extractServerUrl(String url) {
        // jdbc:mysql://localhost:3306/paiswitch?params -> jdbc:mysql://localhost:3306
        int hostEnd = url.indexOf('/', url.indexOf("//") + 2);
        return url.substring(0, hostEnd);
    }
}
