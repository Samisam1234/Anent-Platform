package com.agentplatform.orchestrator.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PublicApiJobConfig {

    @Bean
    public RestTemplate publicApiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        return new RestTemplate(factory);
    }

    @Bean
    @ConfigurationProperties(prefix = "job-sources.openings-mcp")
    public OpeningsMcpJobProperties openingsMcpJobProperties() {
        return new OpeningsMcpJobProperties();
    }
}