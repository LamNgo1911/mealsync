package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.IRecipeGenerationService;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.application.service.AI.IngredientDetectionService;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrator service that coordinates the complete recipe generation workflow:
 * 1. Ingredient detection
 * 2. Recipe generation
 * 3. Image generation (async)
 * 4. User history saving (async)
 * 
 * This service follows the Orchestrator pattern to coordinate multiple services.
 */
@Service
public class RecipeGenerationOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(RecipeGenerationOrchestrator.class);
    
    private final IngredientDetectionService ingredientDetectionService;
    private final AIRecipeService aiRecipeService;
    private final RecipeImageService recipeImageService;
    private final RecipeService recipeService;
    
    public RecipeGenerationOrchestrator(
            IngredientDetectionService ingredientDetectionService,
            AIRecipeService aiRecipeService,
            RecipeImageService recipeImageService,
            RecipeService recipeService) {
        this.ingredientDetectionService = ingredientDetectionService;
        this.aiRecipeService = aiRecipeService;
        this.recipeImageService = recipeImageService;
        this.recipeService = recipeService;
    }
    
    /**
     * Orchestrates the complete workflow: scan image → detect ingredients → generate recipes.
     * Images are generated asynchronously in the background.
     * 
     * @param image Image file to scan
     * @param userPreference User preferences for recipe generation
     * @return List of generated recipes (without images initially)
     */
    public List<RecipeReadDto> scanAndGenerateRecipes(MultipartFile image, UserPreference userPreference) {
        logger.info("Starting scan-and-generate workflow");
        
        // Step 1: Detect ingredients from image (async)
        CompletableFuture<List<DetectedIngredientDto>> ingredientsFuture = 
                ingredientDetectionService.detectRawIngredientsAsync(image);

        // Step 2: Generate recipes as soon as ingredients are ready (pipeline)
        CompletableFuture<List<RecipeReadDto>> recipesFuture = ingredientsFuture
                .thenCompose(ingredients -> {
                    logger.info("Ingredients detected, starting recipe generation");
                    // Note: fetchRecipesFromOpenAIAsync now uses the configured provider (OpenAI or Gemini)
                    return aiRecipeService.fetchRecipesFromOpenAIAsync(ingredients, userPreference);
                });

        // Step 3: Block only to get recipes (images generated async in background)
        List<RecipeReadDto> recipes = recipesFuture.join();
        
        // Step 4: Generate images completely asynchronously (fire and forget - saves ~10s)
        recipeImageService.generateImagesForRecipes(recipes);

        // Note: User history saving should be called by the controller with userId

        logger.info("Scan-and-generate workflow completed. Generated {} recipes", recipes.size());
        return recipes;
    }
    
    /**
     * Generates recipes from provided ingredients.
     * Images are generated asynchronously in the background.
     * 
     * @param ingredients List of detected ingredients
     * @param userPreference User preferences for recipe generation
     * @return List of generated recipes (without images initially)
     */
    public List<RecipeReadDto> generateRecipesFromIngredients(
            List<DetectedIngredientDto> ingredients, 
            UserPreference userPreference) {
        logger.info("Starting recipe generation from ingredients");
        
        // Generate recipes
        // Note: fetchRecipesFromOpenAIAsync now uses the configured provider (OpenAI or Gemini)
        CompletableFuture<List<RecipeReadDto>> recipesFuture = 
                aiRecipeService.fetchRecipesFromOpenAIAsync(ingredients, userPreference);
        
        List<RecipeReadDto> recipes = recipesFuture.join();
        
        // Generate images asynchronously
        recipeImageService.generateImagesForRecipes(recipes);
        
        logger.info("Recipe generation completed. Generated {} recipes", recipes.size());
        return recipes;
    }
    
    /**
     * Saves generated recipes to user's history asynchronously (non-blocking).
     */
    private void saveGeneratedRecipesToUserAsync(List<UUID> recipeIds) {
        // This would need the userId, so we might need to pass it as a parameter
        // For now, keeping it simple - this method signature might need adjustment
        CompletableFuture.runAsync(() -> {
            try {
                // Note: This needs userId - might need to refactor method signature
                // recipeService.addGeneratedRecipesToUser(userId, recipeIds);
                logger.debug("Would save {} generated recipes to user history", recipeIds.size());
            } catch (Exception e) {
                logger.error("Failed to save generated recipes to user history: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Saves generated recipes to user's history asynchronously.
     * 
     * @param userId User ID
     * @param recipeIds List of recipe IDs to save
     */
    public void saveGeneratedRecipesToUserAsync(UUID userId, List<UUID> recipeIds) {
        CompletableFuture.runAsync(() -> {
            try {
                recipeService.addGeneratedRecipesToUser(userId, recipeIds);
                logger.debug("Successfully saved {} generated recipes to user {} history", 
                        recipeIds.size(), userId);
            } catch (Exception e) {
                logger.error("Failed to save generated recipes to user {} history: {}", 
                        userId, e.getMessage(), e);
            }
        });
    }
}

