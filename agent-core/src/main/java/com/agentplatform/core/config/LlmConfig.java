package com.agentplatform.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that enables LLM routing configuration properties.
 * <p>
 * Without this, {@link LlmProperties} would not be bound from application.yml,
 * and {@link LlmProviderRouter} could not be instantiated.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

}