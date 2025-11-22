package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        private AIRecipeService aiRecipeService;

        @Mock
        private RecipeImageService recipeImageService;

        @Mock
        private RecipeService recipeService;

        @InjectMocks
        private RecipeGenerationOrchestrator orchestrator;

        private UserPreference userPreference;
        private DetectedIngredientDto ingredientDto;
        private RecipeReadDto recipeDto;

        @BeforeEach
        void setUp() {
                userPreference = new UserPreference();

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

}
