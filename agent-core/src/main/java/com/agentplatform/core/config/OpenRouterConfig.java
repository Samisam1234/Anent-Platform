package com.agentplatform.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OpenRouterConfig.class);

}