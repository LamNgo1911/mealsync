package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.event.ImageGeneratedEvent;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for generating and managing recipe images.
 * Orchestrates the complete workflow: API call → S3 upload → DB update → Event publishing.
 * Extracted from AIRecipeService to follow Single Responsibility Principle.
 */
@Service
public class RecipeImageService {
    private static final Logger logger = LoggerFactory.getLogger(RecipeImageService.class);
    
    private final IRecipeRepo recipeRepo;
    private final GeminiImageApiClient geminiImageApiClient;
    private final S3Service s3Service;
    private final ApplicationEventPublisher eventPublisher;
    private final PromptLoader promptLoader;
    private final TransactionTemplate transactionTemplate;
    
    public RecipeImageService(
            IRecipeRepo recipeRepo,
            GeminiImageApiClient geminiImageApiClient,
            S3Service s3Service,
            ApplicationEventPublisher eventPublisher,
            PromptLoader promptLoader,
            PlatformTransactionManager transactionManager) {
        this.recipeRepo = recipeRepo;
        this.geminiImageApiClient = geminiImageApiClient;
        this.s3Service = s3Service;
        this.eventPublisher = eventPublisher;
        this.promptLoader = promptLoader;
        // Create TransactionTemplate for programmatic transaction management
        // This allows us to scope transactions to only DB operations, not S3 uploads
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }
    
    /**
     * Generates images for multiple recipes asynchronously.
     * Processes images as they complete (not waiting for all) for better streaming UX.
     * Publishes ImageGeneratedEvent immediately when each image is ready.
     * 
     * @param recipeDtos List of recipes to generate images for
     * @return CompletableFuture that completes when all images are generated
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> generateImagesForRecipes(List<RecipeReadDto> recipeDtos) {
        try {
            if (recipeDtos == null || recipeDtos.isEmpty()) {
                logger.debug("No recipes to generate images for");
                return CompletableFuture.completedFuture(null);
            }

            // Filter recipes that need images
            List<RecipeReadDto> recipesNeedingImages = recipeDtos.stream()
                    .filter(dto -> dto != null && dto.getId() != null 
                            && (dto.getImageUrl() == null || dto.getImageUrl().isEmpty()))
                    .toList();

            if (recipesNeedingImages.isEmpty()) {
                logger.debug("All recipes already have images");
                return CompletableFuture.completedFuture(null);
            }

            logger.info("Generating {} images asynchronously in parallel using Gemini 2.5 Flash Image", 
                    recipesNeedingImages.size());

            // Create individual futures for each recipe - process as they complete
            List<CompletableFuture<Void>> imageFutures = new ArrayList<>();
            
            for (RecipeReadDto dto : recipesNeedingImages) {
                CompletableFuture<Void> imageFuture = generateImageForRecipe(dto)
                        .thenAccept(result -> {
                            // Image processed and event published
                            logger.debug("Image processing completed for recipe: {}", dto.getName());
                        })
                        .exceptionally(throwable -> {
                            logger.error("Failed to process image for recipe {}: {}", 
                                    dto.getName(), throwable.getMessage(), throwable);
                            return null;
                        });
                
                imageFutures.add(imageFuture);
            }

            // Wait for all to complete (but they're processed individually as they finish)
            return CompletableFuture.allOf(imageFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        long successCount = imageFutures.stream()
                                .filter(f -> !f.isCompletedExceptionally())
                                .count();
                        logger.info("All image generation completed: {} successful out of {} total recipes", 
                                successCount, recipesNeedingImages.size());
                    });
        } catch (Exception e) {
            logger.error("Failed to generate images for recipes: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Generates and processes a single recipe image.
     * Processes immediately when ready: uploads to S3, updates DB, publishes event.
     * 
     * @param dto Recipe to generate image for
     * @return CompletableFuture that completes when image is processed
     */
    private CompletableFuture<Void> generateImageForRecipe(RecipeReadDto dto) {
        // Prepare image generation request
        List<String> ingredientNames = dto.getIngredients() != null ?
                dto.getIngredients().stream().map(i -> i.getName()).toList() :
                List.of();
        String description = dto.getDescription() != null ? dto.getDescription() : "";
        
        String prompt = buildImagePrompt(dto.getName(), ingredientNames, description);
        
        // Generate image asynchronously (this is the parallel part)
        return geminiImageApiClient.callGeminiAPISingleAsync(prompt)
                .thenCompose(base64 -> {
                    if (base64 == null || base64.isEmpty()) {
                        logger.warn("No image generated for recipe: {} (recipe ID: {})", dto.getName(), dto.getId());
                        eventPublisher.publishEvent(new ImageGeneratedEvent(
                                this, dto.getId(), dto.getName(), null, false, "No image generated"));
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    // Process image: decode, upload to S3, update DB, publish event
                    return processImageForRecipe(dto, base64);
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to generate image for recipe {} (ID: {}): {}", 
                            dto.getName(), dto.getId(), throwable.getMessage(), throwable);
                    eventPublisher.publishEvent(new ImageGeneratedEvent(
                            this, dto.getId(), dto.getName(), null, false, 
                            "Image generation failed: " + throwable.getMessage()));
                    return null;
                });
    }

    /**
     * Processes a generated image: uploads to S3, updates database, publishes event.
     * Uses TransactionTemplate to scope transactions to only DB operations, not S3 uploads.
     * This prevents connection pool exhaustion when processing multiple images in parallel.
     */
    private CompletableFuture<Void> processImageForRecipe(RecipeReadDto dto, String base64) {
        try {
            // Step 1: Decode and upload to S3 (OUTSIDE transaction - no DB connection needed)
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            String imageUrl = s3Service.uploadImage(imageBytes, dto.getName());

            if (imageUrl == null || imageUrl.isEmpty()) {
                logger.error("S3 service returned empty imageUrl for recipe: {} (ID: {})", 
                        dto.getName(), dto.getId());
                eventPublisher.publishEvent(new ImageGeneratedEvent(
                        this, dto.getId(), dto.getName(), null, false, "S3 upload failed"));
                return CompletableFuture.completedFuture(null);
            }

            // Step 2: Update database (INSIDE transaction - connection held only for DB ops)
            // Use TransactionTemplate to scope transaction to only DB operations
            transactionTemplate.executeWithoutResult(status -> {
                Optional<Recipe> recipeOpt = recipeRepo.getRecipeById(dto.getId());
                if (recipeOpt.isEmpty()) {
                    logger.error("Recipe not found in database for ID: {} (name: {})", 
                            dto.getId(), dto.getName());
                    eventPublisher.publishEvent(new ImageGeneratedEvent(
                            this, dto.getId(), dto.getName(), null, false, "Recipe not found"));
                    return;
                }

                Recipe recipe = recipeOpt.get();
                recipe.setImageUrl(imageUrl);
                recipeRepo.saveRecipe(recipe);
                
                logger.info("Successfully updated recipe {} (ID: {}) with image URL: {}", 
                        dto.getName(), dto.getId(), imageUrl);
            });
            
            // Step 3: Publish success event (OUTSIDE transaction)
            // This triggers SSE streaming to connected clients
            eventPublisher.publishEvent(new ImageGeneratedEvent(
                    this, dto.getId(), dto.getName(), imageUrl, true, null));
            
            return CompletableFuture.completedFuture(null);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid base64 data for recipe {} (ID: {}): {}", 
                    dto.getName(), dto.getId(), e.getMessage());
            eventPublisher.publishEvent(new ImageGeneratedEvent(
                    this, dto.getId(), dto.getName(), null, false, "Invalid image data"));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to process image for recipe {} (ID: {}): {}", 
                    dto.getName(), dto.getId(), e.getMessage(), e);
            eventPublisher.publishEvent(new ImageGeneratedEvent(
                    this, dto.getId(), dto.getName(), null, false, e.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Builds the image generation prompt for a recipe.
     */
    private String buildImagePrompt(String recipeName, List<String> ingredients, String description) {
        String ingredientsStr = (ingredients != null && !ingredients.isEmpty()) 
                ? String.join(", ", ingredients) 
                : "";
        String descriptionStr = description != null ? description : "";
        
        // Use PromptLoader to format the prompt
        return promptLoader.loadAndFormatPrompt("image-generation.txt", Map.of(
                "RECIPE_NAME", recipeName,
                "INGREDIENTS", ingredientsStr,
                "DESCRIPTION", descriptionStr
        ));
    }
}

