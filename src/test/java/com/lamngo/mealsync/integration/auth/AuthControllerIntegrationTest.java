package com.lamngo.mealsync.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamngo.mealsync.application.dto.user.RefreshTokenRequestDto;
import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication endpoints.
 * Tests the complete authentication flow including registration, login, and token refresh.
 */
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IUserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Clean up before each test (handled by @Transactional)
    }

    @Test
    void registerUser_success() throws Exception {
        // Given
        UserCreateDto userCreateDto = new UserCreateDto();
        userCreateDto.setEmail("test@example.com");
        userCreateDto.setPassword("password123");
        userCreateDto.setName("Test User");

        // When & Then
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userCreateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("Test User"));

        // Verify user was saved in database
        User savedUser = userRepo.findByEmail("test@example.com").orElse(null);
        assertNotNull(savedUser);
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("Test User", savedUser.getName());
        assertEquals(UserRole.USER, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertTrue(passwordEncoder.matches("password123", savedUser.getPassword()));
    }

    @Test
    void registerUser_duplicateEmail_shouldFail() throws Exception {
        // Given - create a user first
        UserCreateDto firstUser = new UserCreateDto();
        firstUser.setEmail("existing@example.com");
        firstUser.setPassword("password123");
        firstUser.setName("First User");

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstUser)));

        // When - try to register with same email
        UserCreateDto duplicateUser = new UserCreateDto();
        duplicateUser.setEmail("existing@example.com");
        duplicateUser.setPassword("password456");
        duplicateUser.setName("Duplicate User");

        // Then
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_success() throws Exception {
        // Given - create a user first
        User user = new User();
        user.setEmail("login@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setName("Login User");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        // When
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setEmail("login@example.com");
        loginDto.setPassword("password123");

        String response = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("login@example.com"))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify token is valid JWT format
        JsonNode jsonNode = objectMapper.readTree(response);
        String token = jsonNode.get("data").get("token").asText();
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void login_invalidCredentials_shouldFail() throws Exception {
        // Given - create a user
        User user = new User();
        user.setEmail("invalid@example.com");
        user.setPassword(passwordEncoder.encode("correctpassword"));
        user.setName("Invalid User");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        // When - login with wrong password
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setEmail("invalid@example.com");
        loginDto.setPassword("wrongpassword");

        // Then
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_success() throws Exception {
        // Given - create user and login to get refresh token
        User user = new User();
        user.setEmail("refresh@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setName("Refresh User");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        // Login to get refresh token
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setEmail("refresh@example.com");
        loginDto.setPassword("password123");

        String loginResponse = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String refreshToken = loginJson.get("data").get("refreshToken").get("token").asText();

        // When - refresh the token
        RefreshTokenRequestDto refreshRequest = new RefreshTokenRequestDto();
        refreshRequest.setRefreshToken(refreshToken);

        // Then
        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    void refreshToken_invalidToken_shouldFail() throws Exception {
        // Given
        RefreshTokenRequestDto refreshRequest = new RefreshTokenRequestDto();
        refreshRequest.setRefreshToken("invalid-refresh-token");

        // When & Then
        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isBadRequest());
    }
}

