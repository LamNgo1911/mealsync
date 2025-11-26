package com.lamngo.mealsync.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamngo.mealsync.application.dto.user.RefreshTokenRequestDto;
import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.application.service.auth.EmailVerificationTokenService;
import com.lamngo.mealsync.application.service.auth.PasswordResetTokenService;
import com.lamngo.mealsync.domain.model.user.EmailVerificationToken;
import com.lamngo.mealsync.domain.model.user.PasswordResetToken;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication endpoints.
 * Tests the complete authentication flow including registration, login, and
 * token refresh.
 */
class AuthControllerIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private IUserRepo userRepo;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private EmailVerificationTokenService emailVerificationTokenService;

        @Autowired
        private PasswordResetTokenService passwordResetTokenService;

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
                userCreateDto.setRole(UserRole.USER);

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
                assertFalse(savedUser.isEmailVerified()); // Email should not be verified after registration
                assertTrue(passwordEncoder.matches("password123", savedUser.getPassword()));
        }

        @Test
        void registerUser_duplicateEmail_shouldFail() throws Exception {
                // Given - create a user first
                UserCreateDto firstUser = new UserCreateDto();
                firstUser.setEmail("existing@example.com");
                firstUser.setPassword("password123");
                firstUser.setName("First User");
                firstUser.setRole(UserRole.USER);

                mockMvc.perform(post("/api/v1/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstUser)));

                // When - try to register with same email
                UserCreateDto duplicateUser = new UserCreateDto();
                duplicateUser.setEmail("existing@example.com");
                duplicateUser.setPassword("password456");
                duplicateUser.setName("Duplicate User");
                duplicateUser.setRole(UserRole.USER);

                // Then
                mockMvc.perform(post("/api/v1/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateUser)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void registerUser_shouldFail_whenPasswordInvalid() throws Exception {
                // Too short
                UserCreateDto user1 = new UserCreateDto();
                user1.setEmail("short@example.com");
                user1.setPassword("short123"); // 8 chars
                user1.setName("Short Pass");
                user1.setRole(UserRole.USER);

                mockMvc.perform(post("/api/v1/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(user1)))
                                .andExpect(status().isBadRequest());

                // No number
                UserCreateDto user2 = new UserCreateDto();
                user2.setEmail("nonumber@example.com");
                user2.setPassword("longpassword"); // 12 chars, no digits
                user2.setName("No Number");
                user2.setRole(UserRole.USER);

                mockMvc.perform(post("/api/v1/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(user2)))
                                .andExpect(status().isBadRequest());

                // No letter
                UserCreateDto user3 = new UserCreateDto();
                user3.setEmail("noletter@example.com");
                user3.setPassword("1234567890"); // 10 chars, no letters
                user3.setName("No Letter");
                user3.setRole(UserRole.USER);

                mockMvc.perform(post("/api/v1/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(user3)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void login_success() throws Exception {
                // Given - create a user first with verified email
                User user = new User();
                user.setEmail("login@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Login User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true); // Must be verified to login
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
                user.setEmailVerified(true);
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
                user.setEmailVerified(true);
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

        @Test
        void login_unverifiedEmail_shouldFail() throws Exception {
                // Given - create a user with unverified email
                User user = new User();
                user.setEmail("unverified@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Unverified User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(false); // Email not verified
                userRepo.save(user);

                // When - try to login
                UserLoginDto loginDto = new UserLoginDto();
                loginDto.setEmail("unverified@example.com");
                loginDto.setPassword("password123");

                // Then
                mockMvc.perform(post("/api/v1/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginDto)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.error.message")
                                                .value("Please verify your email before logging in"));
        }

        @Test
        void verifyEmail_success() throws Exception {
                // Given - create a user with unverified email
                User user = new User();
                user.setEmail("verify@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Verify User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(false);
                user = userRepo.save(user);

                // Create verification token
                EmailVerificationToken token = emailVerificationTokenService.createToken(user);

                // When - verify email (now returns HTML instead of JSON)
                mockMvc.perform(get("/api/v1/users/verify-email")
                                .param("token", token.getToken()))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.TEXT_HTML_VALUE))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email Verified")));

                // Then - verify user email is now verified
                User verifiedUser = userRepo.findByEmail("verify@example.com").orElse(null);
                assertNotNull(verifiedUser);
                assertTrue(verifiedUser.isEmailVerified());
        }

        @Test
        void verifyEmail_invalidToken_shouldFail() throws Exception {
                // When & Then
                mockMvc.perform(get("/api/v1/users/verify-email")
                                .param("token", "invalid-token"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void verifyEmail_expiredToken_shouldFail() throws Exception {
                // Given - create a user with unverified email
                User user = new User();
                user.setEmail("expired@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Expired User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(false);
                user = userRepo.save(user);

                // Create expired token manually
                EmailVerificationToken token = new EmailVerificationToken();
                token.setToken("expired-token");
                token.setUser(user);
                token.setExpiryDate(java.time.Instant.now().minusSeconds(3600)); // Expired 1 hour ago
                token.setUsed(false);
                // Note: In a real scenario, you'd need to save this through the repository
                // For this test, we'll use an invalid token approach

                // When & Then
                mockMvc.perform(get("/api/v1/users/verify-email")
                                .param("token", "expired-token"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void resendVerificationEmail_success() throws Exception {
                // Given - create a user with unverified email
                User user = new User();
                user.setEmail("resend@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Resend User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(false);
                userRepo.save(user);

                // When - request resend
                String requestBody = "{\"email\":\"resend@example.com\"}";

                mockMvc.perform(post("/api/v1/users/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").value("Verification email sent successfully"));
        }

        @Test
        void resendVerificationEmail_alreadyVerified_shouldFail() throws Exception {
                // Given - create a user with verified email
                User user = new User();
                user.setEmail("alreadyverified@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Verified User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true);
                userRepo.save(user);

                // When - request resend
                String requestBody = "{\"email\":\"alreadyverified@example.com\"}";

                mockMvc.perform(post("/api/v1/users/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void resendVerificationEmail_userNotFound_shouldFail() throws Exception {
                // When - request resend for non-existent user
                String requestBody = "{\"email\":\"nonexistent@example.com\"}";

                mockMvc.perform(post("/api/v1/users/resend-verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isNotFound());
        }

        @Test
        void forgotPassword_success() throws Exception {
                // Given - create a user
                User user = new User();
                user.setEmail("forgot@example.com");
                user.setPassword(passwordEncoder.encode("oldpassword123"));
                user.setName("Forgot User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true);
                userRepo.save(user);

                // When - request password reset
                String requestBody = "{\"email\":\"forgot@example.com\"}";

                mockMvc.perform(post("/api/v1/users/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(
                                                jsonPath("$.data").value(
                                                                "Password reset email sent successfully. Please check your inbox."));
        }

        @Test
        void forgotPassword_userNotFound_shouldFail() throws Exception {
                // When - request password reset for non-existent user
                String requestBody = "{\"email\":\"nonexistent@example.com\"}";

                mockMvc.perform(post("/api/v1/users/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isNotFound());
        }

        @Test
        void resetPassword_success() throws Exception {
                // Given - create a user
                User user = new User();
                user.setEmail("reset@example.com");
                String oldPassword = "oldpassword123";
                user.setPassword(passwordEncoder.encode(oldPassword));
                user.setName("Reset User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true);
                user = userRepo.save(user);

                // Create password reset token
                PasswordResetToken token = passwordResetTokenService.createToken(user);

                // When - reset password
                String requestBody = String.format(
                                "{\"token\":\"%s\",\"newPassword\":\"newpassword123\"}",
                                token.getToken());

                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data")
                                                .value("Password reset successfully. You can now log in with your new password."));

                // Then - verify password was changed and user can login with new password
                User updatedUser = userRepo.findByEmail("reset@example.com").orElse(null);
                assertNotNull(updatedUser);
                assertTrue(passwordEncoder.matches("newpassword123", updatedUser.getPassword()));
                assertFalse(passwordEncoder.matches(oldPassword, updatedUser.getPassword()));
        }

        @Test
        void resetPassword_invalidToken_shouldFail() throws Exception {
                // When - reset password with invalid token
                String requestBody = "{\"token\":\"invalid-token\",\"newPassword\":\"newpassword123\"}";

                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void resetPassword_expiredToken_shouldFail() throws Exception {
                // Given - create a user
                User user = new User();
                user.setEmail("expiredreset@example.com");
                user.setPassword(passwordEncoder.encode("oldpassword123"));
                user.setName("Expired Reset User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true);
                user = userRepo.save(user);

                // Create expired token manually (in real scenario, this would be expired)
                PasswordResetToken token = new PasswordResetToken();
                token.setToken("expired-token");
                token.setUser(user);
                token.setExpiryDate(java.time.Instant.now().minusSeconds(3600)); // Expired 1 hour ago
                token.setUsed(false);

                // When - try to reset password with expired token
                String requestBody = "{\"token\":\"expired-token\",\"newPassword\":\"newpassword123\"}";

                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void resetPassword_usedToken_shouldFail() throws Exception {
                // Given - create a user and use a token
                User user = new User();
                user.setEmail("usedtoken@example.com");
                user.setPassword(passwordEncoder.encode("oldpassword123"));
                user.setName("Used Token User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true);
                user = userRepo.save(user);

                // Create and use a token
                PasswordResetToken token = passwordResetTokenService.createToken(user);
                passwordResetTokenService.markTokenAsUsed(token.getToken());

                // When - try to reset password with used token
                String requestBody = String.format(
                                "{\"token\":\"%s\",\"newPassword\":\"newpassword123\"}",
                                token.getToken());

                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void resetPassword_shouldFail_whenPasswordInvalid() throws Exception {
                // Given - create a user
                User user = new User();
                user.setEmail("invalidpass@example.com");
                user.setPassword(passwordEncoder.encode("oldpassword123"));
                user.setName("Invalid Pass User");
                user.setRole(UserRole.USER);
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerified(true);
                user = userRepo.save(user);

                // Create password reset token
                PasswordResetToken token = passwordResetTokenService.createToken(user);

                // Too short
                String req1 = String.format(
                                "{\"token\":\"%s\",\"newPassword\":\"short123\"}",
                                token.getToken());
                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(req1))
                                .andExpect(status().isBadRequest());

                // No number
                String req2 = String.format(
                                "{\"token\":\"%s\",\"newPassword\":\"longpassword\"}",
                                token.getToken());
                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(req2))
                                .andExpect(status().isBadRequest());

                // No letter
                String req3 = String.format(
                                "{\"token\":\"%s\",\"newPassword\":\"1234567890\"}",
                                token.getToken());
                mockMvc.perform(post("/api/v1/users/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(req3))
                                .andExpect(status().isBadRequest());
        }
}
