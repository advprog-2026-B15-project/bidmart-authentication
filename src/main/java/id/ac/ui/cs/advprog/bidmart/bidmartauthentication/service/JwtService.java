package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final String CLAIM_SCOPE = "scope";
    private static final String SCOPE_MFA = "mfa";
    private static final long MFA_TOKEN_EXPIRY_MS = 5L * 60 * 1000;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateMfaToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_SCOPE, SCOPE_MFA)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + MFA_TOKEN_EXPIRY_MS))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractMfaEmail(String token) {
        try {
            Claims claims = extractClaims(token);
            if (!SCOPE_MFA.equals(claims.get(CLAIM_SCOPE, String.class))) {
                throw new IllegalArgumentException("Token is not an MFA token");
            }
            if (claims.getExpiration().before(new Date())) {
                throw new IllegalArgumentException("MFA token expired");
            }
            return claims.getSubject();
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid MFA token", e);
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
