package com.agentplatform.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that enables Cerebras configuration properties.
 * <p>
 * Without this, {@link CerebrasProperties} would not be bound from application.yml,
 * and {@link CerebrasProvider} would not be instantiated.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(CerebrasProperties.class)
public class CerebrasConfig {
}