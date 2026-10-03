package com.agentplatform.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that enables Groq configuration properties.
 * <p>
 * Without this, {@link GroqProperties} would not be bound from application.yml,
 * and {@link GroqProvider} would not be instantiated.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(GroqProperties.class)
public class GroqConfig {
}