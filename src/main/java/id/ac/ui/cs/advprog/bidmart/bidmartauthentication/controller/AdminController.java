package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.RefreshTokenService;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.UserEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserEventPublisher eventPublisher;

    public AdminController(UserRepository userRepository,
                           RefreshTokenService refreshTokenService,
                           UserEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/users/{id}/disable")
    public ResponseEntity<Map<String, String>> disableUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails) {
        return userRepository.findById(id).map(user -> {
            user.setLocked(true);
            userRepository.save(user);
            refreshTokenService.revokeAllForUser(user);
            UUID adminId = userRepository.findByEmail(adminDetails.getUsername())
                    .map(a -> a.getId()).orElse(null);
            eventPublisher.publishUserDisabled(user, adminId);
            return ResponseEntity.ok(Map.of("message", "User disabled and sessions revoked"));
        }).orElseGet(() -> ResponseEntity.<Map<String, String>>notFound().build());
    }
}
