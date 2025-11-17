package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.event.ImageGeneratedEvent;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing Server-Sent Events (SSE) streams for image generation.
 * Handles SSE emitter lifecycle and event subscription.
 */
@Service
public class RecipeImageStreamingService {
    private static final Logger logger = LoggerFactory.getLogger(RecipeImageStreamingService.class);
    
    private final RecipeService recipeService;
    private final RecipeImageService recipeImageService;
    
    // Map to track active SSE emitters by recipe IDs
    private final Map<UUID, Set<SseEmitter>> activeEmitters = new ConcurrentHashMap<>();
    
    public RecipeImageStreamingService(
            RecipeService recipeService,
            RecipeImageService recipeImageService) {
        this.recipeService = recipeService;
        this.recipeImageService = recipeImageService;
    }
    
    /**
     * Creates an SSE stream for image generation updates.
     * 
     * @param recipeIds List of recipe IDs to stream images for
     * @return SseEmitter for streaming image updates
     */
    public SseEmitter createImageStream(List<UUID> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            throw new BadRequestException("recipeIds parameter is required");
        }

        logger.info("Starting SSE stream for {} recipe images", recipeIds.size());

        // Create SSE emitter with 5 minute timeout
        SseEmitter emitter = new SseEmitter(300000L);

        // Fetch recipes by IDs in a single transaction to minimize connection usage
        // IMPORTANT: Complete the transaction BEFORE setting up async operations to prevent connection leaks
        List<RecipeReadDto> recipes;
        try {
            recipes = recipeService.getRecipesByIds(recipeIds);
            // Transaction completes here, connection is released
        } catch (Exception e) {
            logger.error("Failed to fetch recipes for streaming: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"Failed to fetch recipes: " + e.getMessage() + "\"}"));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                logger.error("Failed to send error event", ioException);
            }
            return emitter;
        }

        // Register emitter for each recipe ID
        for (UUID recipeId : recipeIds) {
            activeEmitters.computeIfAbsent(recipeId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        }

        // Start image generation asynchronously (will publish events)
        // This happens AFTER the transaction completes, so no connection is held
        CompletableFuture<Void> generationFuture = recipeImageService.generateImagesForRecipes(recipes);
        
        generationFuture.whenComplete((result, throwable) -> {
            // Check if emitter is still valid before sending completion event
            // Client may have already disconnected
            try {
                if (throwable != null) {
                    logger.error("Image generation failed: {}", throwable.getMessage(), throwable);
                    sendEventToEmitter(emitter, "error", 
                            "{\"error\":\"Image generation failed: " + throwable.getMessage() + "\"}");
                } else {
                    logger.info("Image generation completed, closing SSE stream");
                    sendEventToEmitter(emitter, "complete", "{\"message\":\"All images generated\"}");
                }
            } catch (Exception e) {
                logger.debug("Failed to send completion event (client likely disconnected): {}", e.getMessage());
            } finally {
                completeEmitter(emitter, recipeIds, throwable);
            }
        });

        // Handle client disconnect
        emitter.onCompletion(() -> {
            logger.info("SSE stream completed for recipe IDs: {}", recipeIds);
            unregisterEmitter(emitter, recipeIds);
        });
        
        emitter.onTimeout(() -> {
            logger.warn("SSE stream timed out for recipe IDs: {}", recipeIds);
            unregisterEmitter(emitter, recipeIds);
            emitter.complete();
        });
        
        emitter.onError((ex) -> {
            logger.error("SSE stream error for recipe IDs: {}", recipeIds, ex);
            unregisterEmitter(emitter, recipeIds);
            emitter.completeWithError(ex);
        });

        return emitter;
    }
    
    /**
     * Event listener for ImageGeneratedEvent.
     * Sends SSE updates to all registered emitters for the recipe.
     * Note: Not marked as @Async because RecipeImageService.generateImagesForRecipes is already async,
     * and we want to avoid SecurityContext propagation issues.
     */
    @EventListener
    public void handleImageGenerated(ImageGeneratedEvent event) {
        UUID recipeId = event.getRecipeId();
        Set<SseEmitter> emitters = activeEmitters.get(recipeId);
        
        if (emitters == null || emitters.isEmpty()) {
            logger.debug("No active emitters for recipe ID: {}", recipeId);
            return;
        }

        try {
            JSONObject eventData = new JSONObject();
            eventData.put("recipeId", event.getRecipeId().toString());
            eventData.put("recipeName", event.getRecipeName());
            eventData.put("success", event.isSuccess());
            
            if (event.isSuccess() && event.getImageUrl() != null) {
                eventData.put("imageUrl", event.getImageUrl());
            } else if (event.getErrorMessage() != null) {
                eventData.put("error", event.getErrorMessage());
            }

            String eventDataStr = eventData.toString();
            
            // Send to all registered emitters for this recipe
            // Use iterator to safely remove while iterating
            Iterator<SseEmitter> iterator = emitters.iterator();
            while (iterator.hasNext()) {
                SseEmitter emitter = iterator.next();
                try {
                    emitter.send(SseEmitter.event()
                            .name("image-update")
                            .data(eventDataStr));
                    logger.debug("Sent image update via SSE for recipe: {}", recipeId);
                } catch (Exception e) {
                    // Client disconnected or emitter is no longer usable
                    // Remove immediately and log at debug level (this is normal)
                    logger.debug("Failed to send SSE event to emitter for recipe {} (client likely disconnected): {}", 
                            recipeId, e.getMessage());
                    iterator.remove();
                    try {
                        emitter.completeWithError(new IOException("Emitter connection lost"));
                    } catch (Exception ex) {
                        // Ignore - emitter is already closed
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to process image generated event for recipe {}: {}", 
                    recipeId, e.getMessage(), e);
        }
    }
    
    private void sendEventToEmitter(SseEmitter emitter, String eventName, String data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (Exception e) {
            // Client disconnected or emitter is no longer usable
            // This is normal when clients close connections, so log at debug level
            logger.debug("Failed to send {} event to emitter (client likely disconnected): {}", 
                    eventName, e.getMessage());
        }
    }
    
    private void completeEmitter(SseEmitter emitter, List<UUID> recipeIds, Throwable error) {
        if (emitter == null) {
            unregisterEmitter(emitter, recipeIds);
            return;
        }
        try {
            // Check if emitter is still valid before trying to complete it
            // If client already disconnected, the emitter will throw an exception
            if (error != null) {
                emitter.completeWithError(error);
            } else {
                emitter.complete();
            }
        } catch (Exception e) {
            // Client already disconnected or emitter is no longer usable
            // This is normal, so log at debug level
            logger.debug("Failed to complete emitter (client likely disconnected): {}", e.getMessage());
        } finally {
            unregisterEmitter(emitter, recipeIds);
        }
    }
    
    private void unregisterEmitter(SseEmitter emitter, List<UUID> recipeIds) {
        for (UUID recipeId : recipeIds) {
            Set<SseEmitter> emitters = activeEmitters.get(recipeId);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    activeEmitters.remove(recipeId);
                }
            }
        }
    }
}

