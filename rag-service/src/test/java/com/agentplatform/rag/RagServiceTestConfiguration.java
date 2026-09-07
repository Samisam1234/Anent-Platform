package com.agentplatform.rag;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test-only Spring Boot configuration for the rag-service module.
 *
 * <p>The module is a library (no {@code @SpringBootApplication} on main sources),
 * so {@code @DataJpaTest}-based tests cannot locate a {@code @SpringBootConfiguration}
 * to bootstrap the JPA slice. This class provides that anchor and explicitly wires
 * the RAG JPA repositories + entities so {@code RagServiceTest} can run against a
 * real PostgreSQL/pgvector database via Testcontainers.</p>
 */
@SpringBootConfiguration
@EnableJpaRepositories(basePackages = "com.agentplatform.rag.repository")
@EntityScan(basePackages = "com.agentplatform.rag.entity")
public class RagServiceTestConfiguration {
}