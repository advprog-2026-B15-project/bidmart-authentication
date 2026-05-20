package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.SessionResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.RefreshToken;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604800000L);
    }

    @Test
    void createRefreshTokenSavesAndReturnsRawToken() {
        User user = buildUser();
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(user, "Chrome/Win", "127.0.0.1");

        assertNotNull(rawToken);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshTokenThrowsWhenTokenNotFound() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.rotateRefreshToken("invalid-token", new String[1]));
    }

    @Test
    void rotateRefreshTokenThrowsAndRevokesOnReuseDetected() {
        User user = buildUser();
        UUID familyId = UUID.randomUUID();
        RefreshToken revokedToken = buildToken(user, familyId, true,
                LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(revokedToken));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.rotateRefreshToken("reused-token", new String[1]));

        verify(refreshTokenRepository).revokeAllByFamilyId(any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void rotateRefreshTokenThrowsWhenTokenExpired() {
        User user = buildUser();
        RefreshToken expiredToken = buildToken(user, UUID.randomUUID(), false,
                LocalDateTime.now().minusDays(1));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(expiredToken));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.rotateRefreshToken("expired-token", new String[1]));
    }

    @Test
    void revokeTokenDeletesActiveToken() {
        User user = buildUser();
        RefreshToken activeToken = buildToken(user, UUID.randomUUID(), false,
                LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.findByTokenHashAndRevokedFalse(any()))
                .willReturn(Optional.of(activeToken));

        refreshTokenService.revokeToken("some-raw-token");

        verify(refreshTokenRepository).delete(activeToken);
    }

    @Test
    void revokeAllForUserDelegatesDeleteToRepository() {
        User user = buildUser();
        refreshTokenService.revokeAllForUser(user);
        verify(refreshTokenRepository).deleteAllByUser(user);
    }

    @Test
    void revokeSessionByIdThrowsWhenSessionBelongsToDifferentUser() {
        User owner = buildUser();
        owner.setId(UUID.randomUUID());
        User other = buildUser();
        other.setEmail("other@example.com");
        other.setId(UUID.randomUUID());

        UUID sessionId = UUID.randomUUID();
        RefreshToken token = buildToken(owner, UUID.randomUUID(), false,
                LocalDateTime.now().plusDays(7));
        token.setId(sessionId);

        given(refreshTokenRepository.findById(sessionId)).willReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.revokeSessionById(sessionId, other));
    }

    @Test
    void getActiveSessionsMapsTokensToSessionResponse() {
        User user = buildUser();
        UUID familyId = UUID.randomUUID();
        RefreshToken token = buildToken(user, familyId, false, LocalDateTime.now().plusDays(7));
        token.setDeviceInfo("Firefox/Linux");
        token.setIpAddress("10.0.0.1");

        given(refreshTokenRepository.findByUserAndRevokedFalseAndExpiresAtAfter(any(), any()))
                .willReturn(List.of(token));

        List<SessionResponse> sessions = refreshTokenService.getActiveSessions(user);

        assertEquals(1, sessions.size());
        assertEquals("Firefox/Linux", sessions.get(0).getDeviceInfo());
        assertEquals("10.0.0.1", sessions.get(0).getIpAddress());
    }

    private User buildUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setUsername("testuser");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        return user;
    }

    private RefreshToken buildToken(User user, UUID familyId, boolean revoked,
                                    LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken("somehash", user, expiresAt, familyId, null, null);
        token.setRevoked(revoked);
        return token;
    }
}
