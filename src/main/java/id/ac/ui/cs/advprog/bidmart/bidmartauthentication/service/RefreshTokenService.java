package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

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
import java.util.UUID;

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
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
        refreshTokenRepository.save(new RefreshToken(tokenHash, user, expiresAt));
        return rawToken;
    }

    @Transactional
    public User rotateRefreshToken(String rawToken, String[] newRawTokenHolder) {
        String tokenHash = hashToken(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = stored.getUser();
        refreshTokenRepository.delete(stored);

        String newRaw = UUID.randomUUID().toString();
        String newHash = hashToken(newRaw);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
        refreshTokenRepository.save(new RefreshToken(newHash, user, expiresAt));
        newRawTokenHolder[0] = newRaw;
        return user;
    }

    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
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
