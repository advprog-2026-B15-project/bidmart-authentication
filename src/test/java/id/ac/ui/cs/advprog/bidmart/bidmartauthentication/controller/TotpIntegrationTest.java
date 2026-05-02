package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TotpIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_EMAIL = "totp@bidmart.com";
    private static final String TEST_PASSWORD = "password123";
    private static String accessToken;
    private static String totpSecret;

    @Test
    @Order(1)
    void setupRegisterAndVerify() throws Exception {
        registerAndVerify(TEST_EMAIL, "totpuser", TEST_PASSWORD);
        assert mockMvc != null;
    }

    @Test
    @Order(2)
    void loginWithoutTotpReturnsFullTokens() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @Order(3)
    void setupTotpReturnsSecretAndOtpUrl() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/2fa/setup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpAuthUrl").isNotEmpty())
                .andReturn();

        totpSecret = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("secret").asText();
        assertNotNull(totpSecret);
    }

    @Test
    @Order(4)
    void confirmTotpWithValidCodeEnables2fa() throws Exception {
        String code = generateCurrentCode(totpSecret);
        String body = "{\"code\":\"" + code + "\"}";

        mockMvc.perform(post("/api/auth/2fa/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("2FA enabled successfully"));
    }

    @Test
    @Order(5)
    void confirmTotpWithWrongCodeReturns400() throws Exception {
        String body = "{\"code\":\"000000\"}";

        mockMvc.perform(post("/api/auth/2fa/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void loginWith2faEnabledReturnsMfaRequired() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andReturn();

        String mfaToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("mfaToken").asText();

        String code = generateCurrentCode(totpSecret);
        String verifyBody = "{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + code + "\"}";

        MvcResult verifyResult = mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        accessToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @Order(7)
    void verifyMfaWithInvalidTokenReturns401() throws Exception {
        String body = "{\"mfaToken\":\"invalid.token.here\",\"code\":\"123456\"}";

        mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void disableTotpWithValidCodeSucceeds() throws Exception {
        String code = generateCurrentCode(totpSecret);
        String body = "{\"code\":\"" + code + "\"}";

        mockMvc.perform(post("/api/auth/2fa/disable")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("2FA disabled successfully"));
    }

    @Test
    @Order(9)
    void loginAfterDisabling2faReturnsFullTokens() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private String generateCurrentCode(String secret) throws CodeGenerationException {
        SystemTimeProvider timeProvider = new SystemTimeProvider();
        DefaultCodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        long period = 30L;
        long timeSlot = Math.floorDiv(timeProvider.getTime(), period);
        String code = generator.generate(secret, timeSlot);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(generator, timeProvider);
        assertTrue(verifier.isValidCode(secret, code), "Generated TOTP code should be valid");
        return code;
    }
}
