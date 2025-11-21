package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.IngredientDetectionService;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeGenerationOrchestratorTest {

        @Mock
        private IngredientDetectionService ingredientDetectionService;

        @Mock
        private AIRecipeService aiRecipeService;

        @Mock
        private RecipeImageService recipeImageService;

        @Mock
        private RecipeService recipeService;

        @InjectMocks
        private RecipeGenerationOrchestrator orchestrator;

        private UserPreference userPreference;
        private MultipartFile imageFile;
        private DetectedIngredientDto ingredientDto;
        private RecipeReadDto recipeDto;

        @BeforeEach
        void setUp() {
                userPreference = new UserPreference();
                imageFile = new MockMultipartFile("image", "test.jpg", "image/jpeg", "test image content".getBytes());

                ingredientDto = DetectedIngredientDto.builder()
                                .name("chicken")
                                .quantity("200")
                                .unit("grams")
                                .build();

                recipeDto = new RecipeReadDto();
                recipeDto.setId(UUID.randomUUID());
                recipeDto.setName("Test Recipe");
        }

        @Test
        void scanAndGenerateRecipes_shouldReturnRecipes() {
                // Given
                List<DetectedIngredientDto> ingredients = List.of(ingredientDto);
                List<RecipeReadDto> recipes = List.of(recipeDto);

                CompletableFuture<List<DetectedIngredientDto>> ingredientsFuture = CompletableFuture
                                .completedFuture(ingredients);
                CompletableFuture<List<RecipeReadDto>> recipesFuture = CompletableFuture.completedFuture(recipes);

                when(ingredientDetectionService.detectRawIngredientsAsync(any(MultipartFile.class)))
                                .thenReturn(ingredientsFuture);
                when(aiRecipeService.fetchRecipesFromOpenAIAsync(anyList(), any(UserPreference.class)))
                                .thenReturn(recipesFuture);
                when(recipeImageService.generateImagesForRecipes(anyList()))
                                .thenReturn(CompletableFuture.completedFuture(null));

                // When
                List<RecipeReadDto> result = orchestrator.scanAndGenerateRecipes(imageFile, userPreference);

                // Then
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(recipeDto.getId(), result.get(0).getId());
                verify(ingredientDetectionService, times(1)).detectRawIngredientsAsync(any(MultipartFile.class));
                verify(aiRecipeService, times(1)).fetchRecipesFromOpenAIAsync(anyList(), any(UserPreference.class));
                verify(recipeImageService, times(1)).generateImagesForRecipes(anyList());
        }

        @Test
        void generateRecipesFromIngredients_shouldReturnRecipes() {
                // Given
                List<DetectedIngredientDto> ingredients = List.of(ingredientDto);
                List<RecipeReadDto> recipes = List.of(recipeDto);

                CompletableFuture<List<RecipeReadDto>> recipesFuture = CompletableFuture.completedFuture(recipes);

                when(aiRecipeService.fetchRecipesFromOpenAIAsync(anyList(), any(UserPreference.class)))
                                .thenReturn(recipesFuture);
                when(recipeImageService.generateImagesForRecipes(anyList()))
                                .thenReturn(CompletableFuture.completedFuture(null));

                // When
                List<RecipeReadDto> result = orchestrator.generateRecipesFromIngredients(ingredients, userPreference);

                // Then
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(recipeDto.getId(), result.get(0).getId());
                verify(aiRecipeService, times(1)).fetchRecipesFromOpenAIAsync(anyList(), any(UserPreference.class));
                verify(recipeImageService, times(1)).generateImagesForRecipes(anyList());
        }

        @Test
        void saveGeneratedRecipesToUserAsync_shouldCallService() {
                // Given
                UUID userId = UUID.randomUUID();
                List<UUID> recipeIds = List.of(UUID.randomUUID(), UUID.randomUUID());

                doNothing().when(recipeService).addGeneratedRecipesToUser(any(UUID.class), anyList());

                // When
                orchestrator.saveGeneratedRecipesToUserAsync(userId, recipeIds);

                // Then
                // Wait a bit for async operation
                try {
                        Thread.sleep(100);
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                }
                verify(recipeService, timeout(1000).times(1)).addGeneratedRecipesToUser(userId, recipeIds);
        }

        @Test
        void scanAndGenerateRecipes_shouldHandleEmptyIngredients() {
                // Given
                List<DetectedIngredientDto> emptyIngredients = List.of();
                List<RecipeReadDto> emptyRecipes = List.of();

                CompletableFuture<List<DetectedIngredientDto>> ingredientsFuture = CompletableFuture
                                .completedFuture(emptyIngredients);
                CompletableFuture<List<RecipeReadDto>> recipesFuture = CompletableFuture.completedFuture(emptyRecipes);

                when(ingredientDetectionService.detectRawIngredientsAsync(any(MultipartFile.class)))
                                .thenReturn(ingredientsFuture);
                when(aiRecipeService.fetchRecipesFromOpenAIAsync(anyList(), any(UserPreference.class)))
                                .thenReturn(recipesFuture);
                when(recipeImageService.generateImagesForRecipes(anyList()))
                                .thenReturn(CompletableFuture.completedFuture(null));

                // When
                List<RecipeReadDto> result = orchestrator.scanAndGenerateRecipes(imageFile, userPreference);

                // Then
                assertNotNull(result);
                assertTrue(result.isEmpty());
        }
}
