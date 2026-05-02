package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingUserEventPublisher implements UserEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingUserEventPublisher.class);

    @Override
    public void publishUserRegistered(User user) {
        LOG.info("[EVENT] user.registered userId={} email={}", user.getId(), user.getEmail());
    }

    @Override
    public void publishUserEmailVerified(User user) {
        LOG.info("[EVENT] user.email.verified userId={}", user.getId());
    }

    @Override
    public void publishUserLoggedIn(User user) {
        LOG.info("[EVENT] user.logged.in userId={}", user.getId());
    }

    @Override
    public void publishPasswordReset(User user) {
        LOG.info("[EVENT] password.reset userId={}", user.getId());
    }

    @Override
    public void publishUserDisabled(User user, UUID adminId) {
        LOG.info("[EVENT] user.disabled userId={} adminId={}", user.getId(), adminId);
    }

    @Override
    public void publishSessionRevoked(UUID userId, UUID sessionId) {
        LOG.info("[EVENT] session.revoked userId={} sessionId={}", userId, sessionId);
    }
}
