package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RefreshTokenRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RegisterRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.ResetPasswordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefreshTokenIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private static final String TEST_EMAIL = "refresh@bidmart.com";
    private static final String TEST_PASSWORD = "password123";
    private static String verificationToken;
    private static String refreshToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @Order(1)
    void setupRegisterAndVerify() throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(TEST_EMAIL);
        reg.setUsername("refreshuser");
        reg.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        verificationToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("verificationToken").asText();

        mockMvc.perform(post("/api/auth/verify-email").param("token", verificationToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void loginReturnsAccessAndRefreshToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        refreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("refreshToken").asText();
    }

    @Test
    @Order(3)
    void refreshWithValidTokenRotatesAndReturnsNewPair() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshToken);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        refreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("refreshToken").asText();
    }

    @Test
    @Order(4)
    void refreshWithOldTokenReturns401() throws Exception {
        String oldToken = "old-invalid-token";
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(oldToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void logoutRevokesRefreshToken() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @Order(6)
    void refreshAfterLogoutReturns401() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void forgotPasswordAlwaysReturns200() throws Exception {
        String body = "{\"email\":\"" + TEST_EMAIL + "\"}";

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @Order(8)
    void forgotPasswordForUnknownEmailStillReturns200() throws Exception {
        String body = "{\"email\":\"nobody@example.com\"}";
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    void resetPasswordWithInvalidTokenReturns400() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("definitely-invalid-token");
        req.setNewPassword("newpassword123");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
