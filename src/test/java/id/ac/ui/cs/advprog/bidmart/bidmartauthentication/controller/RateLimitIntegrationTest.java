package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_EMAIL = "ratelimit@bidmart.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String WRONG_PASSWORD = "wrongpassword";

    @Test
    @Order(1)
    void setupRegisterAndVerify() throws Exception {
        registerAndVerify(TEST_EMAIL, "ratelimituser", TEST_PASSWORD);
        assert mockMvc != null;
    }

    @Test
    @Order(2)
    void firstFourAttemptsReturn401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(WRONG_PASSWORD);

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @Order(3)
    void fifthAttemptStillReturn401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(WRONG_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void sixthAttemptIsRateLimitedWith429() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(WRONG_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @Order(5)
    void correctPasswordAlsoRateLimitedAfterBucketExhausted() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());
    }
}
