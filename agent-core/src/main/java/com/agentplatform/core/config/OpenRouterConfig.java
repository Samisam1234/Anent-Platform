package com.agentplatform.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that enables OpenRouter configuration properties.
 * <p>
 * Without this, {@link OpenRouterProperties} would not be bound from application.yml,
 * and {@link OpenRouterProvider} would not be instantiated.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterConfig {
}