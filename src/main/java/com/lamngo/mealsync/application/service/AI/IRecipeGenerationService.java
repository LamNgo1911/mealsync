package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.domain.model.user.UserPreference;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for recipe generation services.
 * Allows flexible switching between different AI providers (OpenAI, Gemini, etc.)
 */
public interface IRecipeGenerationService {
    
    /**
     * Generates recipes from detected ingredients asynchronously.
     * 
     * @param ingredients List of detected ingredients with quantities and units
     * @param userPreference User preferences (dietary restrictions, cuisines, etc.)
     * @return CompletableFuture that completes with a list of generated recipes
     */
    CompletableFuture<List<RecipeReadDto>> generateRecipesAsync(
            List<DetectedIngredientDto> ingredients, 
            UserPreference userPreference);
}

