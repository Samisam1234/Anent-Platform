package com.agentplatform.orchestrator.job;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Wiring for the optional public job source adapter. Provides the {@link RestTemplate}
 * the adapter uses; timeouts keep failures fast so a slow public API cannot stall the
 * job search. No network is ever touched until {@link PublicApiJobSource} is enabled.
 */
@Configuration
public class PublicApiJobConfig {

    @Bean
    public RestTemplate publicApiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}