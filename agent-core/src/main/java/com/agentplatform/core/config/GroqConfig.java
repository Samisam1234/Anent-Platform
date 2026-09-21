package com.agentplatform.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GroqConfig.class);

}