package com.lamngo.mealsync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:testdb",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
	"GOOGLE_APPLICATION_CREDENTIALS=",
	"JWT_SECRET=test-secret-key-for-testing-purposes-only-minimum-256-bits",
	"JWT_EXPIRATION=86400000",
	"JWT_REFRESH_EXPIRATION=604800000"
})
class MealSyncApplicationTests {

	@Test
	void contextLoads() {
	}

}
