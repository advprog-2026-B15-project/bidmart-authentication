package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RefreshTokenRequest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_EMAIL = "session@bidmart.com";
    private static final String TEST_PASSWORD = "password123";
    private static String accessToken;
    private static String refreshTokenA;
    private static String refreshTokenB;
    private static String sessionIdA;

    @Test
    @Order(1)
    void setupRegisterAndVerify() throws Exception {
        registerAndVerify(TEST_EMAIL, "sessionuser", TEST_PASSWORD);
        assert mockMvc != null;
    }

    @Test
    @Order(2)
    void firstLoginCreatesSession() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(body).get("accessToken").asText();
        refreshTokenA = objectMapper.readTree(body).get("refreshToken").asText();
    }

    @Test
    @Order(3)
    void secondLoginCreatesAnotherSession() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        refreshTokenB = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("refreshToken").asText();
    }

    @Test
    @Order(4)
    void listSessionsReturnsTwoActiveSessions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        sessionIdA = objectMapper.readTree(result.getResponse().getContentAsString())
                .get(0).get("id").asText();
    }

    @Test
    @Order(5)
    void revokeOneSessionAndTokenNoLongerWorks() throws Exception {
        mockMvc.perform(delete("/api/auth/sessions/" + sessionIdA)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Session revoked"));

        // refreshTokenA's session was just deleted — refresh must return 401
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshTokenA);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // Only one session remains
        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Order(6)
    void revokeNonExistentSessionReturns404() throws Exception {
        String fakeId = "00000000-0000-0000-0000-000000000000";
        mockMvc.perform(delete("/api/auth/sessions/" + fakeId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void revokeAllSessionsClearsListAndInvalidatesRemainingToken() throws Exception {
        mockMvc.perform(delete("/api/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All sessions revoked"));

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // refreshTokenB's session was deleted — refresh must return 401
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshTokenB);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void refreshTokenReuseDetectionRevokesEntireFamily() throws Exception {
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(TEST_EMAIL);
        loginReq.setPassword(TEST_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String originalToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Rotate once — marks original as revoked
        RefreshTokenRequest rotateReq = new RefreshTokenRequest();
        rotateReq.setRefreshToken(originalToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateReq)))
                .andExpect(status().isOk());

        // Reuse the already-rotated token — triggers family revocation
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void listSessionsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/sessions"))
                .andExpect(status().isUnauthorized());
    }
}
