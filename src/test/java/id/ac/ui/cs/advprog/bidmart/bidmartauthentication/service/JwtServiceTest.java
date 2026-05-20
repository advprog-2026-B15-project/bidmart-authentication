package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm!!";
    private static final long TEST_EXPIRATION = 86400000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", TEST_EXPIRATION);
    }

    @Test
    void generateTokenReturnsNonNullString() {
        String token = jwtService.generateToken("user@example.com");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractEmailReturnsCorrectSubject() {
        String email = "user@example.com";
        String token = jwtService.generateToken(email);
        assertEquals(email, jwtService.extractEmail(token));
    }

    @Test
    void isTokenValidReturnsTrueForMatchingUser() {
        String email = "user@example.com";
        String token = jwtService.generateToken(email);
        UserDetails userDetails = buildUserDetails(email);
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValidReturnsFalseForDifferentUser() {
        String token = jwtService.generateToken("user@example.com");
        UserDetails otherUser = buildUserDetails("other@example.com");
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void generateMfaTokenReturnsNonNullString() {
        String mfaToken = jwtService.generateMfaToken("mfa@example.com");
        assertNotNull(mfaToken);
        assertFalse(mfaToken.isBlank());
    }

    @Test
    void extractMfaEmailReturnsCorrectSubject() {
        String email = "mfa@example.com";
        String mfaToken = jwtService.generateMfaToken(email);
        assertEquals(email, jwtService.extractMfaEmail(mfaToken));
    }

    @Test
    void extractMfaEmailThrowsForRegularToken() {
        String regularToken = jwtService.generateToken("user@example.com");
        assertThrows(IllegalArgumentException.class,
                () -> jwtService.extractMfaEmail(regularToken));
    }

    @Test
    void extractEmailThrowsForInvalidToken() {
        assertThrows(Exception.class, () -> jwtService.extractEmail("not.a.jwt"));
    }

    private UserDetails buildUserDetails(String email) {
        return new User(email, "password", Collections.emptyList());
    }
}
