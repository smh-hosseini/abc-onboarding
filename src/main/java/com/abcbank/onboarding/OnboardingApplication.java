package com.abcbank.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application for ABC Bank Digital Onboarding System.
 *
 * This application implements Hexagonal Architecture (Ports & Adapters) with:
 * - Domain layer: Pure business logic (no framework dependencies)
 * - Application layer: Use case orchestration
 * - Adapter layer: Framework-specific implementations
 * - Infrastructure layer: Cross-cutting concerns
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class OnboardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingApplication.class, args);
    }
}
