package com.lamngo.mealsync.application.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Spring event published when an image is generated for a recipe.
 * Used for decoupling image generation from SSE streaming.
 */
public class ImageGeneratedEvent extends ApplicationEvent {
    private final UUID recipeId;
    private final String recipeName;
    private final String imageUrl;
    private final boolean success;
    private final String errorMessage;
    
    public ImageGeneratedEvent(Object source, UUID recipeId, String recipeName, String imageUrl, boolean success, String errorMessage) {
        super(source);
        this.recipeId = recipeId;
        this.recipeName = recipeName;
        this.imageUrl = imageUrl;
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    public UUID getRecipeId() {
        return recipeId;
    }
    
    public String getRecipeName() {
        return recipeName;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}

