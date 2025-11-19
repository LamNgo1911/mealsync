package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.application.service.AI.IngredientDetectionService;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
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
        long workflowStart = System.nanoTime();
        
        // Step 1: Detect ingredients from image (async)
        long detectionStart = System.nanoTime();
        CompletableFuture<List<DetectedIngredientDto>> ingredientsFuture = 
                ingredientDetectionService.detectRawIngredientsAsync(image);
        ingredientsFuture.whenComplete((ingredients, throwable) -> {
            long detectionMs = Duration.ofNanos(System.nanoTime() - detectionStart).toMillis();
            if (throwable == null) {
                int count = ingredients != null ? ingredients.size() : 0;
                logger.info("Ingredient detection completed in {} ms ({} ingredients)", detectionMs, count);
            } else {
                logger.warn("Ingredient detection failed after {} ms: {}", detectionMs, throwable.getMessage());
            }
        });

        // Step 2: Generate recipes as soon as ingredients are ready (pipeline)
        CompletableFuture<List<RecipeReadDto>> recipesFuture = ingredientsFuture
                .thenCompose(ingredients -> {
                    logger.info("Ingredients detected, starting recipe generation");
                    // Note: fetchRecipesFromOpenAIAsync now uses the configured provider (OpenAI or Gemini)
                    return aiRecipeService.fetchRecipesFromOpenAIAsync(ingredients, userPreference);
                });

        // Step 3: Block only to get recipes (images generated async in background)
        long generationWaitStart = System.nanoTime();
        List<RecipeReadDto> recipes = recipesFuture.join();
        long generationMs = Duration.ofNanos(System.nanoTime() - generationWaitStart).toMillis();
        logger.info("Recipe generation future completed in {} ms ({} recipes)", generationMs, recipes.size());
        
        // Step 4: Generate images completely asynchronously (fire and forget - saves ~10s)
        recipeImageService.generateImagesForRecipes(recipes);

        // Note: User history saving should be called by the controller with userId
        long workflowMs = Duration.ofNanos(System.nanoTime() - workflowStart).toMillis();
        logger.info("Scan-and-generate workflow completed in {} ms. Generated {} recipes", workflowMs, recipes.size());
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
        long workflowStart = System.nanoTime();
        
        // Generate recipes
        // Note: fetchRecipesFromOpenAIAsync now uses the configured provider (OpenAI or Gemini)
        CompletableFuture<List<RecipeReadDto>> recipesFuture = 
                aiRecipeService.fetchRecipesFromOpenAIAsync(ingredients, userPreference);
        
        long generationWaitStart = System.nanoTime();
        List<RecipeReadDto> recipes = recipesFuture.join();
        long generationMs = Duration.ofNanos(System.nanoTime() - generationWaitStart).toMillis();
        logger.info("Recipe generation future completed in {} ms ({} recipes)", generationMs, recipes.size());
        
        // Generate images asynchronously
        recipeImageService.generateImagesForRecipes(recipes);
        
        long workflowMs = Duration.ofNanos(System.nanoTime() - workflowStart).toMillis();
        logger.info("Recipe generation request completed in {} ms. Generated {} recipes", workflowMs, recipes.size());
        return recipes;
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

