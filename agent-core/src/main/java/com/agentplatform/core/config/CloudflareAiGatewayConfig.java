package com.agentplatform.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that enables Cloudflare AI Gateway configuration properties.
 * <p>
 * Without this, {@link CloudflareAiGatewayProperties} would not be bound from application.yml,
 * and {@link CloudflareAiGatewayProvider} would not be instantiated.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(CloudflareAiGatewayProperties.class)
public class CloudflareAiGatewayConfig {
}