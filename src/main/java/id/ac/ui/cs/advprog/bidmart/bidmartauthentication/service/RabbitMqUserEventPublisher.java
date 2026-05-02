package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.config.RabbitMqConfig;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class RabbitMqUserEventPublisher implements UserEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMqUserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqUserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishUserRegistered(User user) {
        publish("user.registered", buildPayload(user.getId(), user.getEmail()));
    }

    @Override
    public void publishUserEmailVerified(User user) {
        publish("user.email.verified", buildPayload(user.getId(), user.getEmail()));
    }

    @Override
    public void publishUserLoggedIn(User user) {
        publish("user.logged.in", buildPayload(user.getId(), user.getEmail()));
    }

    @Override
    public void publishPasswordReset(User user) {
        publish("password.reset", buildPayload(user.getId(), user.getEmail()));
    }

    @Override
    public void publishUserDisabled(User user, UUID adminId) {
        Map<String, Object> payload = buildPayload(user.getId(), user.getEmail());
        payload.put("adminId", adminId);
        publish("user.disabled", payload);
    }

    @Override
    public void publishSessionRevoked(UUID userId, UUID sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("sessionId", sessionId);
        payload.put("timestamp", Instant.now().toString());
        publish("session.revoked", payload);
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey, payload);
        } catch (Exception e) {
            LOG.warn("[EVENT] Failed to publish {} to RabbitMQ: {}", routingKey, e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(UUID userId, String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }
}
