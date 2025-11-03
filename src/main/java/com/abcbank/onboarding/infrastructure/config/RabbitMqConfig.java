package com.abcbank.onboarding.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for domain event publishing.
 *
 * Architecture:
 * - Topic Exchange: Routes events based on routing keys
 * - Notification Queue: Receives user-facing events (created, approved, rejected, submitted)
 * - Audit Queue: Receives all events for audit trail
 *
 * Routing Strategy:
 * - event.notification.*: Routes to notification queue
 * - event.audit.*: Routes to audit queue
 * - event.#: Catch-all for audit
 */
@Configuration
public class RabbitMqConfig {

    public static final String DOMAIN_EVENTS_EXCHANGE = "onboarding.domain.events";
    public static final String NOTIFICATION_QUEUE = "onboarding.notifications";
    public static final String AUDIT_QUEUE = "onboarding.audit";

    public static final String NOTIFICATION_ROUTING_KEY = "event.notification.*";
    public static final String AUDIT_ROUTING_KEY = "event.#";

    /**
     * Topic exchange for routing domain events.
     * Durable: survives broker restarts
     */
    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(DOMAIN_EVENTS_EXCHANGE, true, false);
    }

    /**
     * Queue for notification events that trigger user-facing communications.
     * Durable: messages survive broker restarts
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", 86400000) // 24 hours
                .build();
    }

    /**
     * Queue for audit events - all events are logged for compliance.
     * Durable with longer TTL for audit retention.
     */
    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .withArgument("x-message-ttl", 2592000000L) // 30 days
                .build();
    }

    /**
     * Binding for notification events.
     * Routes: event.notification.application_created, event.notification.application_approved, etc.
     */
    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(domainEventsExchange)
                .with(NOTIFICATION_ROUTING_KEY);
    }

    /**
     * Binding for audit events - catches all events.
     * Routes: event.* (all events)
     */
    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder
                .bind(auditQueue)
                .to(domainEventsExchange)
                .with(AUDIT_ROUTING_KEY);
    }

    /**
     * JSON message converter for serializing domain events.
     * Uses Jackson to convert Java objects to JSON.
     * Configured with JavaTimeModule for LocalDateTime serialization.
     */
    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate configured with JSON message converter.
     * Used by RabbitMqEventPublisher to send messages.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
