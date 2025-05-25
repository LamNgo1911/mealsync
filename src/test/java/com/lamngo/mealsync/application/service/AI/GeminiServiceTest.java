package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeIngredient;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.presentation.error.GeminiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiServiceTest {
    private RecipeMapper recipeMapper;
    private IRecipeRepo recipeRepo;
    private IRecipeIngredient recipeIngredientRepo;
    private ImageGeneratorService imageGeneratorService;
    private S3Service s3Service;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        recipeMapper = mock(RecipeMapper.class);
        recipeRepo = mock(IRecipeRepo.class);
        recipeIngredientRepo = mock(IRecipeIngredient.class);
        imageGeneratorService = mock(ImageGeneratorService.class);
        s3Service = mock(S3Service.class);
        geminiService = new GeminiService(recipeMapper, recipeRepo, recipeIngredientRepo, imageGeneratorService, s3Service);
        ReflectionTestUtils.setField(geminiService, "geminiApiBaseUrl", "https://fake.api");
        ReflectionTestUtils.setField(geminiService, "geminiApiKey", "fake-key");
    }

    @Test
    void generateRecipes_emptyIngredients_throwsException() {
        UserPreference preference = new UserPreference();
        Exception ex = assertThrows(GeminiServiceException.class, () ->
                geminiService.generateRecipes(Collections.emptyList(), preference));
        assertTrue(ex.getMessage().contains("empty or null"));
    }

    @Test
    void generateRecipes_success() {
        // Arrange
        List<String> ingredients = List.of("egg", "milk");
        UserPreference preference = new UserPreference();
        RecipeReadDto mockDto = mock(RecipeReadDto.class);
        GeminiService spyGeminiService = spy(geminiService);
        doReturn(List.of(mockDto)).when(spyGeminiService).fetchRecipesFromGemini(anyList(), any());
        doNothing().when(spyGeminiService).addImageToRecipe(any());

        // Act
        List<RecipeReadDto> result = spyGeminiService.generateRecipes(ingredients, preference);
        // Assert
        assertEquals(1, result.size());
        assertSame(mockDto, result.get(0));
    }

    @Test
    void generateRecipes_nullPreference_defaultsAndSucceeds() {
        List<String> ingredients = List.of("egg");
        RecipeReadDto mockDto = mock(RecipeReadDto.class);
        GeminiService spyGeminiService = spy(geminiService);
        doReturn(List.of(mockDto)).when(spyGeminiService).fetchRecipesFromGemini(anyList(), any());
        doNothing().when(spyGeminiService).addImageToRecipe(any());
        List<RecipeReadDto> result = spyGeminiService.generateRecipes(ingredients, null);
        assertEquals(1, result.size());
    }
}
