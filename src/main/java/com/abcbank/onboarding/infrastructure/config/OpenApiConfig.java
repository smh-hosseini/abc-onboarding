package com.abcbank.onboarding.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:onboarding-service}")
    private String applicationName;

    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ABC Bank Digital Customer Onboarding API")
                        .version(applicationVersion)
                        .description("Production-ready digital customer onboarding system for ABC Bank (Netherlands)")
                        .contact(new Contact()
                                .name("ABC Bank API Team")
                                .email("api@abc.nl")
                                .url("https://www.abc.nl"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.abc.nl/license")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.abc.nl").description("Production")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token for APPLICANT (15-min expiry)"))
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("OAuth2 with MFA for COMPLIANCE_OFFICER and ADMIN")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
