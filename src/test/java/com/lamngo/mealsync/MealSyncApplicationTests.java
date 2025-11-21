package com.lamngo.mealsync;

import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.IngredientDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import com.lamngo.mealsync.application.service.AWS.S3Service;

@SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"JWT_SECRET=test-secret-key-for-testing-purposes-only-minimum-256-bits",
		"JWT_EXPIRATION=86400000",
		"JWT_REFRESH_EXPIRATION=604800000",
		"OPENAI_API_BASE_URL=http://localhost",
		"OPENAI_API_KEY=test-key",
		"AWS_ACCESS_KEY_ID=test-key",
		"AWS_SECRET_ACCESS_KEY=test-secret",
		"AWS_REGION=us-east-1",
		"AWS_S3_BUCKET_NAME=test-bucket",
		"GEMINI_API_KEY=test-key",
		"GEMINI_API_BASE_URL=http://localhost",
		"GOOGLE_CLIENT_ID=test-client-id",
		"AWS_SES_FROM_EMAIL=test@example.com",
		"RECIPE_GENERATION_PROVIDER=openai",
		"RECIPE_GENERATION_MODE=parallel"
})
class MealSyncApplicationTests {

	// Mock external services to avoid initialization issues
	@MockitoBean
	private S3Service s3Service;

	@MockitoBean
	private AIRecipeService aiRecipeService;

	@MockitoBean
	private IngredientDetectionService ingredientDetectionService;

	@Test
	void contextLoads() {
		// Context loads successfully with mocked external services
		// This test verifies that the Spring application context can be loaded
		// with all required beans and configurations
	}

}
