package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.event.ImageGeneratedEvent;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
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

        // Fetch recipes by IDs
        List<RecipeReadDto> recipes;
        try {
            recipes = recipeIds.stream()
                    .map(id -> recipeService.getRecipeById(id))
                    .toList();
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

        // Start image generation (will publish events)
        CompletableFuture<Void> generationFuture = recipeImageService.generateImagesForRecipes(recipes);
        
        generationFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Image generation failed: {}", throwable.getMessage(), throwable);
                sendEventToEmitter(emitter, "error", 
                        "{\"error\":\"Image generation failed: " + throwable.getMessage() + "\"}");
                completeEmitter(emitter, recipeIds, throwable);
            } else {
                logger.info("Image generation completed, closing SSE stream");
                sendEventToEmitter(emitter, "complete", "{\"message\":\"All images generated\"}");
                completeEmitter(emitter, recipeIds, null);
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
     */
    @EventListener
    @Async
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
            List<SseEmitter> toRemove = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("image-update")
                            .data(eventDataStr));
                    logger.debug("Sent image update via SSE for recipe: {}", recipeId);
                } catch (Exception e) {
                    logger.warn("Failed to send SSE event to emitter for recipe {}: {}", 
                            recipeId, e.getMessage());
                    toRemove.add(emitter);
                }
            }
            
            // Remove failed emitters
            toRemove.forEach(emitter -> {
                emitters.remove(emitter);
                try {
                    emitter.completeWithError(new IOException("Emitter connection lost"));
                } catch (Exception e) {
                    // Ignore
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to process image generated event for recipe {}: {}", 
                    recipeId, e.getMessage(), e);
        }
    }
    
    private void sendEventToEmitter(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            logger.error("Failed to send {} event to emitter", eventName, e);
        }
    }
    
    private void completeEmitter(SseEmitter emitter, List<UUID> recipeIds, Throwable error) {
        try {
            if (error != null) {
                emitter.completeWithError(error);
            } else {
                emitter.complete();
            }
        } catch (Exception e) {
            logger.error("Failed to complete emitter", e);
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

