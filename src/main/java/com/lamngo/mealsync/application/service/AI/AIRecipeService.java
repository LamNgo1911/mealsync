package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.presentation.error.AIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Facade service for recipe generation that delegates to a configured AI provider.
 * Supports flexible switching between OpenAI and Gemini via dependency injection.
 * 
 * This service maintains backward compatibility by providing the same public API
 * while internally using IRecipeGenerationService implementations.
 */
@Service
public class AIRecipeService {
    private static final Logger logger = LoggerFactory.getLogger(AIRecipeService.class);

    private final IRecipeGenerationService recipeGenerationService;


    /**
     * Constructor that injects the recipe generation service.
     * The provider is selected via RecipeGenerationConfig based on the 
     * RECIPE_GENERATION_PROVIDER property (defaults to "openai").
     * 
     * To switch providers, set in application.properties or environment:
     * - RECIPE_GENERATION_PROVIDER=openai (uses OpenAI gpt-4o-mini)
     * - RECIPE_GENERATION_PROVIDER=gemini (uses Gemini gemini-2.5-flash)
     */
    @Autowired
    public AIRecipeService(IRecipeGenerationService recipeGenerationService) {
        this.recipeGenerationService = recipeGenerationService;
        logger.info("AIRecipeService initialized with provider: {}", 
                recipeGenerationService.getClass().getSimpleName());
    }

    /**
     * Generates recipes from a list of detected ingredients with quantities and units.
     * This method maintains backward compatibility with existing code.
     * 
     * @param ingredients List of detected ingredients
     * @param userPreference User preferences for recipe generation
     * @return List of generated recipes
     */
    public List<RecipeReadDto> generateRecipes(List<DetectedIngredientDto> ingredients, UserPreference userPreference) {
        if (ingredients == null || ingredients.isEmpty()) {
            logger.warn("Ingredients list is empty or null");
            throw new AIServiceException("Ingredients list is empty or null");
        }
        if (userPreference == null) {
            userPreference = new UserPreference();
            logger.debug("No user preference provided, using default");
        }

        logger.info("Generating recipes from {} ingredients using {}", 
                ingredients.size(), recipeGenerationService.getClass().getSimpleName());
        try {
            // Use the configured recipe generation service
            List<RecipeReadDto> recipeDtos = recipeGenerationService.generateRecipesAsync(ingredients, userPreference)
                    .join();
            
            logger.info("Successfully generated {} recipes", recipeDtos.size());

            // Generate images completely asynchronously - don't wait (saves ~10s)
            addImagesToRecipesBatchAsync(recipeDtos);  // Fire and forget

            return recipeDtos;
        } catch (AIServiceException e) {
            logger.error("AI service error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error generating recipes: {}", e.getMessage(), e);
            throw new AIServiceException("Failed to generate recipes: " + e.getMessage());
        }
    }

    /**
     * Fetches recipes asynchronously using the configured AI provider.
     * This method maintains backward compatibility with existing code.
     * Note: Method name still says "OpenAI" for backward compatibility,
     * but it actually uses the configured provider (OpenAI or Gemini).
     * 
     * @param ingredients List of detected ingredients
     * @param userPreference User preferences for recipe generation
     * @return CompletableFuture that completes with a list of generated recipes
     */
    public CompletableFuture<List<RecipeReadDto>> fetchRecipesFromOpenAIAsync(
            List<DetectedIngredientDto> ingredients, UserPreference userPreference) {
        // Note: Method name still says "OpenAI" for backward compatibility,
        // but it actually uses the configured provider (OpenAI or Gemini)
        return recipeGenerationService.generateRecipesAsync(ingredients, userPreference);
    }

    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     * Use RecipeImageService.generateImagesForRecipes() instead.
     */
    @Deprecated
    public void addImageToRecipe(RecipeReadDto dto) {
        logger.warn("addImageToRecipe is deprecated. Use RecipeImageService instead.");
        // This method is kept for backward compatibility but should not be used
    }

    /**
     * @deprecated This method is kept for backward compatibility.
     * The ingredient key generation logic is now in OpenAIRecipeService and GeminiRecipeService.
     */
    @Deprecated
    public String generateIngredientKey(String recipeName) {
        if (recipeName == null) return null;
        return recipeName.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "_");
    }
    
    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     * Use RecipeImageService.generateImagesForRecipes() instead.
     */
    @Deprecated
    public CompletableFuture<Void> addImagesToRecipesBatch(List<RecipeReadDto> recipeDtos) {
        logger.warn("addImagesToRecipesBatch is deprecated. Use RecipeImageService instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     * Use RecipeImageService.generateImagesForRecipes() instead.
     */
    @Deprecated
    public CompletableFuture<Void> addImagesToRecipesBatchAsync(List<RecipeReadDto> recipeDtos) {
        logger.warn("addImagesToRecipesBatchAsync is deprecated. Use RecipeImageService instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated Image generation has been moved to ImageGenerationService.
     * Use ImageGenerationService.generateImagesForRecipes() with Spring Events instead.
     */
    @Deprecated
    public CompletableFuture<Void> addImagesToRecipesBatchAsync(
            List<RecipeReadDto> recipeDtos, 
            Object onImageReady) {
        logger.warn("addImagesToRecipesBatchAsync with callback is deprecated. Use RecipeImageService with Spring Events instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     */
    @Deprecated
    public CompletableFuture<Void> addImageToRecipeAsync(RecipeReadDto dto) {
        logger.warn("addImageToRecipeAsync is deprecated. Use RecipeImageService instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated User history saving has been moved to RecipeGenerationOrchestrator.
     */
    @Deprecated
    public CompletableFuture<Void> saveGeneratedRecipesToUserAsync(UUID userId, List<UUID> recipeIds) {
        logger.warn("saveGeneratedRecipesToUserAsync is deprecated. Use RecipeGenerationOrchestrator instead.");
        return CompletableFuture.completedFuture(null);
    }
}
