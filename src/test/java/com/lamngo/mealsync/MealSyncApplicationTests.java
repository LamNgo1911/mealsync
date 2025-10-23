package com.lamngo.mealsync;

import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import com.lamngo.mealsync.application.service.AWS.S3Service;

@SpringBootTest
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
	"STABILITY_API_KEY=test-key",
	"STABILITY_API_URL=http://localhost",
	"GOOGLE_CLIENT_ID=test-client-id"
})
class MealSyncApplicationTests {

	// Mock external services to avoid initialization issues
	@MockitoBean(name = "s3Service")
	private S3Service s3Service;
	
	@MockitoBean(name = "geminiService")
	private AIRecipeService aiRecipeService;

	@Test
	void contextLoads() {
		// Context loads successfully with mocked external services
	}

}
