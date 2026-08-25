package com.agentplatform.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Agent Platform Spring Boot application.
 *
 * <p>The {@code scanBasePackages} ensures that @Configuration and @Service
 * beans from sibling modules (agent-core, orchestrator) are discovered.</p>
 */
@SpringBootApplication(scanBasePackages = "com.agentplatform")
public class AgentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
