package com.agentplatform.memory;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test-only Spring Boot configuration for the memory-service module.
 *
 * <p>The module is a library (no {@code @SpringBootApplication} on main sources),
 * so {@code @DataJpaTest}-based tests cannot locate a {@code @SpringBootConfiguration}
 * to bootstrap the JPA slice. This class provides that anchor and explicitly wires
 * JPA repositories + entities so {@link PersistentConversationStoreTest} can run
 * against a real in-memory H2 database.</p>
 */
@SpringBootConfiguration
@EnableJpaRepositories(basePackages = "com.agentplatform.memory.repository")
@EntityScan(basePackages = "com.agentplatform.memory.entity")
public class MemoryServiceTestConfiguration {
}
