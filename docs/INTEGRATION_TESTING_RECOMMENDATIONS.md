# Integration Testing Recommendations

## Current State Analysis

### ✅ What You Have
- **Unit Tests**: Comprehensive coverage for controllers, services, and DTOs
- **Test Infrastructure**: Spring Boot Test, H2 database, Mockito
- **13 Unit Test Files**: Covering all major components

### ❌ What's Missing
- **Integration Tests**: No end-to-end API tests
- **Database Integration Tests**: No tests with real database interactions
- **Security Integration Tests**: No tests for JWT authentication flow
- **End-to-End Flow Tests**: No tests for complete user workflows

## Why Integration Tests Are Important

1. **Catch Integration Issues**: Unit tests mock dependencies, but integration tests verify real interactions
2. **Database Validation**: Ensure JPA queries, transactions, and relationships work correctly
3. **Security Testing**: Verify JWT authentication, authorization, and token refresh flows
4. **API Contract Testing**: Ensure endpoints work correctly with real HTTP requests
5. **End-to-End Scenarios**: Test complete user workflows (register → login → create recipe → save)

## Recommended Integration Tests

### 🔐 Priority 1: Authentication Flow (Critical)

#### 1.1 User Registration & Login Flow
```java
- POST /api/v1/users/register → Verify user created in DB
- POST /api/v1/users/login → Verify JWT token returned
- Verify password is hashed in database
- Verify user preferences are created
```

#### 1.2 Token Refresh Flow
```java
- Login → Get refresh token
- POST /api/v1/users/refresh → Verify new access token
- Verify old refresh token is invalidated
- Test expired refresh token rejection
- Test revoked refresh token rejection
```

#### 1.3 Security & Authorization
```java
- Unauthenticated access to protected endpoints → 401
- User accessing another user's data → 403
- Admin-only endpoints → Verify role-based access
```

### 🍽️ Priority 2: Recipe Management (High)

#### 2.1 Recipe CRUD Operations
```java
- POST /api/v1/recipes → Create recipe → Verify saved in DB
- GET /api/v1/recipes/{id} → Retrieve recipe
- PUT /api/v1/recipes/{id} → Update recipe → Verify changes persisted
- DELETE /api/v1/recipes/{id} → Delete recipe → Verify removed from DB
```

#### 2.2 Recipe Pagination
```java
- GET /api/v1/recipes?offset=0&limit=10 → Verify pagination
- GET /api/v1/recipes?offset=10&limit=10 → Verify next page
- Verify totalElements and hasNext are correct
```

#### 2.3 Recipe Filtering
```java
- GET /api/v1/recipes?cuisines=Italian → Filter by cuisine
- GET /api/v1/recipes?tags=quick,easy → Filter by tags
- GET /api/v1/recipes?ingredients=chicken,rice → Filter by ingredients
- Verify filters work correctly with database queries
```

### 👤 Priority 3: User Recipe Relationships (High)

#### 3.1 Save/Unsave Recipe
```java
- POST /api/v1/recipes/save → Save recipe to user
- Verify UserRecipe relationship created in DB
- GET /api/v1/recipes/saved → Verify saved recipes returned
- DELETE /api/v1/recipes/save → Unsave recipe
- Verify UserRecipe removed from DB
```

#### 3.2 Generated Recipes History
```java
- POST /api/v1/recipes/generate-recipes → Generate recipes
- Verify recipes saved to user's generated history
- GET /api/v1/recipes/recent → Verify generated recipes returned
- Verify pagination works for generated recipes
```

### 🔍 Priority 4: Ingredient Detection (Medium)

#### 4.1 Text-Based Detection
```java
- POST /api/v1/recipes/detect-ingredients-from-text
- Verify ingredients are detected and returned
- Test with various text formats
```

#### 4.2 Image-Based Detection
```java
- POST /api/v1/recipes/detect-ingredients (multipart)
- Verify image upload handling
- Mock OpenAI API response
- Verify ingredients extracted correctly
```

### 📊 Priority 5: End-to-End User Flows (Medium)

#### 5.1 Complete Recipe Generation Flow
```java
1. Register user
2. Login → Get token
3. Detect ingredients from text
4. Generate recipes from ingredients
5. Save recipe to user
6. Get saved recipes
7. Verify all data persisted correctly
```

#### 5.2 User Preference Updates
```java
1. Register user
2. Update user preferences
3. Generate recipes → Verify preferences applied
4. Verify preferences persisted in DB
```

## Implementation Strategy

### Test Structure
```
src/test/java/com/lamngo/mealsync/integration/
├── auth/
│   ├── AuthControllerIntegrationTest.java
│   └── RefreshTokenIntegrationTest.java
├── recipe/
│   ├── RecipeControllerIntegrationTest.java
│   └── RecipePaginationIntegrationTest.java
├── user/
│   └── UserRecipeIntegrationTest.java
└── e2e/
    └── CompleteUserFlowIntegrationTest.java
```

### Test Configuration

#### Base Test Class
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public abstract class BaseIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;
    
    @Autowired
    protected TestRestTemplate restTemplate;
    
    @Autowired
    protected IUserRepo userRepo;
    
    @Autowired
    protected IRecipeRepo recipeRepo;
    
    // Helper methods for creating test data
}
```

### Key Testing Patterns

#### 1. Database Setup/Teardown
- Use `@Transactional` for automatic rollback
- Use `@Sql` for test data setup
- Use `@DirtiesContext` when needed

#### 2. Authentication Helpers
```java
protected String getAuthToken(String email, String password) {
    // Login and extract token
}

protected String getAdminToken() {
    // Get admin user token
}
```

#### 3. Mock External Services
```java
@MockBean
private IngredientDetectionService ingredientDetectionService;

@MockBean
private AIRecipeService aiRecipeService;

@MockBean
private S3Service s3Service;
```

## Test Execution Strategy

### Maven Configuration
Add to `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Running Tests
```bash
# Run only unit tests
mvn test

# Run only integration tests
mvn verify -Dtest=*IntegrationTest

# Run all tests
mvn verify
```

## Metrics to Track

1. **Test Coverage**: Aim for 80%+ overall coverage
2. **Integration Test Count**: Target 20-30 integration tests
3. **Critical Path Coverage**: 100% coverage for auth and core recipe flows
4. **Execution Time**: Keep integration tests under 30 seconds total

## Next Steps

1. ✅ Create base integration test class
2. ✅ Implement authentication flow tests
3. ✅ Implement recipe CRUD integration tests
4. ✅ Implement user-recipe relationship tests
5. ✅ Implement end-to-end flow tests
6. ✅ Add to CI/CD pipeline

## Example Test Structure

See `src/test/java/com/lamngo/mealsync/integration/` for example implementations.

