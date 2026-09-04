package com.agentplatform.ui;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA persistence wiring for the whole platform.
 *
 * <p>Kept off {@link AgentPlatformApplication} so {@code @WebMvcTest} slice
 * tests load only the web layer: Spring Data JPA registers the shared
 * {@code EntityManagerFactory} eagerly, which fails when no EntityManagerFactory
 * bean exists (the web slice excludes JPA auto-configuration).</p>
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.agentplatform")
@EntityScan(basePackages = "com.agentplatform")
public class PersistenceConfig {
}