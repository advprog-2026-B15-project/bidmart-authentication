package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;

import java.util.UUID;

public interface UserEventPublisher {

    void publishUserRegistered(User user);

    void publishUserEmailVerified(User user);

    void publishUserLoggedIn(User user);

    void publishPasswordReset(User user);

    void publishUserDisabled(User user, UUID adminId);

    void publishSessionRevoked(UUID userId, UUID sessionId);
}
