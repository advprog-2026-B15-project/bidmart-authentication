package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    void generateSecretReturnsNonNullNonBlankString() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isBlank());
    }

    @Test
    void generateSecretProducesUniqueSecrets() {
        String secretA = totpService.generateSecret();
        String secretB = totpService.generateSecret();
        assertFalse(secretA.equals(secretB));
    }

    @Test
    void getOtpAuthUrlContainsExpectedParts() {
        String secret = totpService.generateSecret();
        String url = totpService.getOtpAuthUrl(secret, "user@example.com");
        assertTrue(url.startsWith("otpauth://totp/BidMart:"));
        assertTrue(url.contains("secret=" + secret));
        assertTrue(url.contains("issuer=BidMart"));
        assertTrue(url.contains("algorithm=SHA1"));
        assertTrue(url.contains("digits=6"));
        assertTrue(url.contains("period=30"));
    }

    @Test
    void verifyCodeReturnsFalseForInvalidCode() {
        String secret = totpService.generateSecret();
        assertFalse(totpService.verifyCode(secret, "000000"));
    }

    @Test
    void verifyCodeReturnsFalseForBlankCode() {
        String secret = totpService.generateSecret();
        assertFalse(totpService.verifyCode(secret, ""));
    }
}
