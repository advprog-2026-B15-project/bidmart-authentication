package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public AdminController(UserRepository userRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/users/{id}/disable")
    public ResponseEntity<Map<String, String>> disableUser(@PathVariable UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setLocked(true);
            userRepository.save(user);
            refreshTokenService.revokeAllForUser(user);
            return ResponseEntity.ok(Map.of("message", "User disabled and sessions revoked"));
        }).orElseGet(() -> ResponseEntity.<Map<String, String>>notFound().build());
    }
}
