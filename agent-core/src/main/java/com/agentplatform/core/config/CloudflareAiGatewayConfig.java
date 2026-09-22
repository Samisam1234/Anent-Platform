package com.agentplatform.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CloudflareAiGatewayConfig.class);

}