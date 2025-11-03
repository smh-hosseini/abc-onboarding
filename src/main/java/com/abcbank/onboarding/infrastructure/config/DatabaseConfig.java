package com.abcbank.onboarding.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.abcbank.onboarding.adapter.out.persistence.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {
    // HikariCP configuration is in application.yml
    // This enables JPA repositories and auditing
}
