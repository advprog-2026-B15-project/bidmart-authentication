package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RegisterRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private static final String ADMIN_EMAIL = "admin@bidmart.com";
    private static final String TARGET_EMAIL = "victim@bidmart.com";
    private static final String PASSWORD = "password123";
    private static String adminToken;
    private static String targetUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @Order(1)
    void setupAdminAndTargetUsers() throws Exception {
        registerAndVerify(ADMIN_EMAIL, "adminuser");
        registerAndVerify(TARGET_EMAIL, "targetuser");

        User admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Admin user not found"));
        admin.setRole("ADMIN");
        userRepository.save(admin);

        User target = userRepository.findByEmail(TARGET_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Target user not found"));
        targetUserId = target.getId().toString();
    }

    @Test
    @Order(2)
    void adminLoginSucceeds() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(ADMIN_EMAIL);
        req.setPassword(PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        adminToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @Order(3)
    void disableUserAsAdminReturns200AndRevokesSession() throws Exception {
        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @Order(4)
    void disableNonexistentUserReturns404() throws Exception {
        String randomId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/admin/users/" + randomId + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void disableUserWithoutAdminRoleReturns403() throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail("regular@bidmart.com");
        reg.setUsername("regularuser");
        reg.setPassword(PASSWORD);

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        String vt = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("verificationToken").asText();
        mockMvc.perform(post("/api/auth/verify-email").param("token", vt))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail("regular@bidmart.com");
        login.setPassword(PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String regularToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/disable")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void disableUserWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/disable"))
                .andExpect(status().isUnauthorized());
    }

    private void registerAndVerify(String email, String username) throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(email);
        reg.setUsername(username);
        reg.setPassword(PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        String vt = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("verificationToken").asText();

        mockMvc.perform(post("/api/auth/verify-email").param("token", vt))
                .andExpect(status().isOk());
    }
}
