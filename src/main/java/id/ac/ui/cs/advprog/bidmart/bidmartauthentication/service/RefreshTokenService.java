package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.SessionResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.RefreshToken;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RefreshTokenService {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String createRefreshToken(User user, String deviceInfo, String ipAddress) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        UUID familyId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
        refreshTokenRepository.save(
                new RefreshToken(tokenHash, user, expiresAt, familyId, deviceInfo, ipAddress));
        return rawToken;
    }

    @Transactional
    public User rotateRefreshToken(String rawToken, String[] newRawTokenHolder) {
        String tokenHash = hashToken(rawToken);

        Optional<RefreshToken> anyMatch = refreshTokenRepository.findByTokenHash(tokenHash);
        if (anyMatch.isEmpty()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        RefreshToken stored = anyMatch.get();
        if (stored.isRevoked()) {
            // Reuse of a rotated token — potential theft, revoke entire family
            refreshTokenRepository.revokeAllByFamilyId(stored.getFamilyId(), LocalDateTime.now());
            throw new IllegalArgumentException("Refresh token reuse detected; all sessions revoked");
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = stored.getUser();
        UUID familyId = stored.getFamilyId();

        // Mark old token as revoked (enables reuse detection)
        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        String newRaw = UUID.randomUUID().toString();
        String newHash = hashToken(newRaw);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
        refreshTokenRepository.save(
                new RefreshToken(newHash, user, expiresAt, familyId,
                        stored.getDeviceInfo(), stored.getIpAddress()));
        newRawTokenHolder[0] = newRaw;
        return user;
    }

    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
    }

    @Transactional
    public void revokeSessionById(UUID sessionId, User owner) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!token.getUser().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Session does not belong to current user");
        }
        refreshTokenRepository.delete(token);
    }

    public List<SessionResponse> getActiveSessions(User user) {
        return refreshTokenRepository
                .findByUserAndRevokedFalseAndExpiresAtAfter(user, LocalDateTime.now())
                .stream()
                .map(t -> new SessionResponse(
                        t.getId(), t.getDeviceInfo(), t.getIpAddress(),
                        t.getCreatedAt(), t.getExpiresAt()))
                .collect(Collectors.toList());
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
