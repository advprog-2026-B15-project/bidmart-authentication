package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
        ReflectionTestUtils.setField(rateLimitService, "maxAttempts", 3);
        ReflectionTestUtils.setField(rateLimitService, "windowSeconds", 900);
    }

    @Test
    void allowsAttemptsUpToMaximum() {
        String ip = "10.0.0.1";
        assertTrue(rateLimitService.isLoginAllowed(ip));
        assertTrue(rateLimitService.isLoginAllowed(ip));
        assertTrue(rateLimitService.isLoginAllowed(ip));
    }

    @Test
    void blocksAfterMaxAttemptsExceeded() {
        String ip = "10.0.0.2";
        rateLimitService.isLoginAllowed(ip);
        rateLimitService.isLoginAllowed(ip);
        rateLimitService.isLoginAllowed(ip);
        assertFalse(rateLimitService.isLoginAllowed(ip));
    }

    @Test
    void differentIpAddressesHaveIndependentBuckets() {
        String ipA = "192.168.1.1";
        String ipB = "192.168.1.2";
        rateLimitService.isLoginAllowed(ipA);
        rateLimitService.isLoginAllowed(ipA);
        rateLimitService.isLoginAllowed(ipA);
        assertFalse(rateLimitService.isLoginAllowed(ipA));
        assertTrue(rateLimitService.isLoginAllowed(ipB));
    }

    @Test
    void firstAttemptIsAlwaysAllowed() {
        assertTrue(rateLimitService.isLoginAllowed("10.10.10.10"));
    }
}
