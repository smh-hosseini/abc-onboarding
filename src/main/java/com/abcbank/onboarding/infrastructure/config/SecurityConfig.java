package com.abcbank.onboarding.infrastructure.config;

import com.abcbank.onboarding.infrastructure.security.JwtAuthenticationEntryPoint;
import com.abcbank.onboarding.infrastructure.security.JwtAuthenticationFilter;
import com.abcbank.onboarding.infrastructure.security.RateLimitFilter;
import com.abcbank.onboarding.infrastructure.security.SessionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SessionFilter sessionFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(RateLimitFilter rateLimitFilter,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          SessionFilter sessionFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.sessionFilter = sessionFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for API (using JWT tokens)
                .csrf(AbstractHttpConfigurer::disable)

                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless session (JWT-based)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no authentication)
                        .requestMatchers(HttpMethod.POST, "/api/v1/onboarding/applications").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/onboarding/applications/*/send-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/onboarding/applications/*/verify-otp").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/onboarding/applications/*/status").permitAll()

                        // Authentication endpoints (public for login/register, protected for logout/me)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/send-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()

                        // Actuator endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Swagger/OpenAPI
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                        // Applicant endpoints (JWT required)
                        .requestMatchers("/api/v1/applicant/**").hasRole("APPLICANT")

                        // Compliance officer endpoints
                        .requestMatchers("/api/v1/compliance/**").hasRole("COMPLIANCE_OFFICER")

                        // Admin endpoints
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Security headers
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                )

                // Exception handling
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                // Add Rate Limit filter first (Order 0)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                // Add JWT filter after Rate Limit (Order 1)
                .addFilterAfter(jwtAuthenticationFilter, RateLimitFilter.class)

                // Add Session filter after JWT filter (Order 2)
                .addFilterAfter(sessionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://abc.nl"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
