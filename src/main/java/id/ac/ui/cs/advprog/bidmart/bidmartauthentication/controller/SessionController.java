package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.SessionResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.RefreshTokenService;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.UserEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/sessions")
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;

    public SessionController(RefreshTokenService refreshTokenService,
                             UserRepository userRepository,
                             UserEventPublisher eventPublisher) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        return ResponseEntity.ok(refreshTokenService.getActiveSessions(user));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> revokeSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID sessionId) {
        User user = resolveUser(userDetails);
        try {
            refreshTokenService.revokeSessionById(sessionId, user);
            eventPublisher.publishSessionRevoked(user.getId(), sessionId);
            return ResponseEntity.ok(Map.of("message", "Session revoked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> revokeAllSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        refreshTokenService.revokeAllForUser(user);
        return ResponseEntity.ok(Map.of("message", "All sessions revoked"));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
    }
}
