package com.lamngo.mealsync.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for integration tests.
 * Provides common setup and helper methods.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false",
    "JWT_SECRET=test-secret-key-for-testing-purposes-only-minimum-256-bits-required-for-security",
    "JWT_EXPIRATION=86400000",
    "JWT_REFRESH_EXPIRATION=604800000",
    "OPENAI_API_BASE_URL=http://localhost",
    "OPENAI_API_KEY=test-key",
    "AWS_ACCESS_KEY_ID=test-key",
    "AWS_SECRET_ACCESS_KEY=test-secret",
    "AWS_REGION=us-east-1",
    "AWS_S3_BUCKET_NAME=test-bucket",
    "STABILITY_API_KEY=test-key",
    "STABILITY_API_URL=http://localhost",
    "GOOGLE_CLIENT_ID=test-client-id"
})
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected IUserRepo userRepo;

    @Autowired
    protected IRecipeRepo recipeRepo;

    @Autowired
    protected org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // Mock external services to avoid actual API calls
    @MockitoBean
    protected com.lamngo.mealsync.application.service.AI.IngredientDetectionService ingredientDetectionService;

    @MockitoBean
    protected com.lamngo.mealsync.application.service.AI.AIRecipeService aiRecipeService;

    @MockitoBean
    protected com.lamngo.mealsync.application.service.AWS.S3Service s3Service;

    /**
     * Helper method to register a test user with properly encoded password and preferences
     */
    protected User createTestUser(String email, String password, String name) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(UserRole.USER);
        user.setStatus(com.lamngo.mealsync.domain.model.user.UserStatus.ACTIVE);
        user.setEmailVerified(true); // Set to true for test users to allow login

        // Create user preference
        com.lamngo.mealsync.domain.model.user.UserPreference preference = 
            new com.lamngo.mealsync.domain.model.user.UserPreference();
        preference.setDietaryRestrictions(new java.util.ArrayList<>());
        preference.setFavoriteCuisines(new java.util.ArrayList<>());
        preference.setDislikedIngredients(new java.util.ArrayList<>());
        preference.setUser(user);
        user.setUserPreference(preference);

        return userRepo.save(user);
    }

    /**
     * Helper method to get authentication token by logging in
     */
    protected String getAuthToken(String email, String password) throws Exception {
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setEmail(email);
        loginDto.setPassword(password);

        String response = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract token from response (simplified - adjust based on your response structure)
        // This assumes the response contains a token field
        return extractTokenFromResponse(response);
    }

    /**
     * Extract token from login response
     * Adjust this based on your actual response structure
     */
    private String extractTokenFromResponse(String response) throws Exception {
        // Parse JSON response and extract token
        // This is a simplified version - adjust based on your UserInfoDto structure
        com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(response);
        if (jsonNode.has("data") && jsonNode.get("data").has("token")) {
            return jsonNode.get("data").get("token").asText();
        }
        throw new RuntimeException("Token not found in response: " + response);
    }

    /**
     * Helper method to create a test user and return their auth token
     */
    protected String createUserAndGetToken(String email, String password, String name) throws Exception {
        // First, register the user
        UserCreateDto userCreateDto = new UserCreateDto();
        userCreateDto.setEmail(email);
        userCreateDto.setPassword(password);
        userCreateDto.setName(name);

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userCreateDto)));

        // Then login to get token
        return getAuthToken(email, password);
    }
}

