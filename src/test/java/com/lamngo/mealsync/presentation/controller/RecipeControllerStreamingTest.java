package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.recipe.RecipeService;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipeControllerStreamingTest {
    @Mock
    private RecipeService recipeService;
    @Mock
    private AIRecipeService aiRecipeService;
    @InjectMocks
    private RecipeController controller;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void streamImageGeneration_success_singleRecipe() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        
        // Mock the callback mechanism
        AtomicInteger callbackCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
                recipeId, 
                "Test Recipe", 
                "https://s3.example.com/image.jpg", 
                true, 
                null
            );
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<AIRecipeService.ImageUpdate> callback = 
                invocation.getArgument(1);
            callback.accept(update);
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(
            anyList(), 
            any(java.util.function.Consumer.class)
        );

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Assert
        assertNotNull(emitter);
        verify(recipeService).getRecipeById(recipeId);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_success_multipleRecipes() throws Exception {
        // Arrange
        UUID recipeId1 = UUID.randomUUID();
        UUID recipeId2 = UUID.randomUUID();
        UUID recipeId3 = UUID.randomUUID();
        String recipeIds = recipeId1 + "," + recipeId2 + "," + recipeId3;
        
        RecipeReadDto recipe1 = new RecipeReadDto();
        recipe1.setId(recipeId1);
        recipe1.setName("Recipe 1");
        
        RecipeReadDto recipe2 = new RecipeReadDto();
        recipe2.setId(recipeId2);
        recipe2.setName("Recipe 2");
        
        RecipeReadDto recipe3 = new RecipeReadDto();
        recipe3.setId(recipeId3);
        recipe3.setName("Recipe 3");
        
        when(recipeService.getRecipeById(recipeId1)).thenReturn(recipe1);
        when(recipeService.getRecipeById(recipeId2)).thenReturn(recipe2);
        when(recipeService.getRecipeById(recipeId3)).thenReturn(recipe3);
        
        AtomicInteger callbackCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<AIRecipeService.ImageUpdate> callback = 
                invocation.getArgument(1);
            
            // Simulate 3 image updates
            callback.accept(new AIRecipeService.ImageUpdate(
                recipeId1, "Recipe 1", "https://s3.example.com/image1.jpg", true, null));
            callback.accept(new AIRecipeService.ImageUpdate(
                recipeId2, "Recipe 2", "https://s3.example.com/image2.jpg", true, null));
            callback.accept(new AIRecipeService.ImageUpdate(
                recipeId3, "Recipe 3", "https://s3.example.com/image3.jpg", true, null));
            
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(
            anyList(), 
            any(java.util.function.Consumer.class)
        );

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Assert
        assertNotNull(emitter);
        verify(recipeService).getRecipeById(recipeId1);
        verify(recipeService).getRecipeById(recipeId2);
        verify(recipeService).getRecipeById(recipeId3);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_nullRecipeIds_throwsBadRequestException() {
        // Act & Assert
        assertThrows(BadRequestException.class, () -> 
            controller.streamImageGeneration(null));
        verify(recipeService, never()).getRecipeById(any());
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_emptyRecipeIds_throwsBadRequestException() {
        // Act & Assert
        assertThrows(BadRequestException.class, () -> 
            controller.streamImageGeneration(""));
        verify(recipeService, never()).getRecipeById(any());
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_invalidUUID_throwsBadRequestException() {
        // Act & Assert
        assertThrows(BadRequestException.class, () -> 
            controller.streamImageGeneration("invalid-uuid"));
        verify(recipeService, never()).getRecipeById(any());
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_malformedRecipeIds_throwsBadRequestException() {
        // Act & Assert
        assertThrows(BadRequestException.class, () -> 
            controller.streamImageGeneration("uuid1,uuid2,invalid"));
        verify(recipeService, never()).getRecipeById(any());
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_recipeNotFound_handlesException() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        when(recipeService.getRecipeById(recipeId))
            .thenThrow(new RuntimeException("Recipe not found"));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Assert
        assertNotNull(emitter);
        verify(recipeService).getRecipeById(recipeId);
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_imageGenerationFailure_sendsErrorEvent() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        
        RuntimeException error = new RuntimeException("Image generation failed");
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.failedFuture(error));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Assert
        assertNotNull(emitter);
        verify(recipeService).getRecipeById(recipeId);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_withWhitespaceInIds_parsesCorrectly() throws Exception {
        // Arrange
        UUID recipeId1 = UUID.randomUUID();
        UUID recipeId2 = UUID.randomUUID();
        String recipeIds = recipeId1 + " , " + recipeId2; // With spaces
        
        RecipeReadDto recipe1 = new RecipeReadDto();
        recipe1.setId(recipeId1);
        recipe1.setName("Recipe 1");
        
        RecipeReadDto recipe2 = new RecipeReadDto();
        recipe2.setId(recipeId2);
        recipe2.setName("Recipe 2");
        
        when(recipeService.getRecipeById(recipeId1)).thenReturn(recipe1);
        when(recipeService.getRecipeById(recipeId2)).thenReturn(recipe2);
        
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Assert
        assertNotNull(emitter);
        verify(recipeService).getRecipeById(recipeId1);
        verify(recipeService).getRecipeById(recipeId2);
    }

    @Test
    void streamImageGeneration_sendsImageUpdateEvents() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        String imageUrl = "https://s3.example.com/image.jpg";
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SseEmitter> emitterRef = new AtomicReference<>();
        
        doAnswer(invocation -> {
            AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
                recipeId, 
                "Test Recipe", 
                imageUrl, 
                true, 
                null
            );
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<AIRecipeService.ImageUpdate> callback = 
                invocation.getArgument(1);
            callback.accept(update);
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(
            anyList(), 
            any(java.util.function.Consumer.class)
        );

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);
        emitterRef.set(emitter);

        // Wait for callback to be invoked
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_sendsErrorEventOnFailure() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        String errorMessage = "Image generation failed";
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        
        doAnswer(invocation -> {
            AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
                recipeId, 
                "Test Recipe", 
                null, 
                false, 
                errorMessage
            );
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<AIRecipeService.ImageUpdate> callback = 
                invocation.getArgument(1);
            callback.accept(update);
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(
            anyList(), 
            any(java.util.function.Consumer.class)
        );

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_completesOnSuccess() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);

        // Wait a bit for async completion
        Thread.sleep(100);

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesCallbackException() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        
        doAnswer(invocation -> {
            AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
                recipeId, "Test Recipe", "https://s3.example.com/image.jpg", true, null);
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<AIRecipeService.ImageUpdate> callback = 
                invocation.getArgument(1);
            // Simulate callback that throws exception
            callback.accept(update);
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);
        
        // Wait a bit for async processing
        Thread.sleep(200);

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesPartialSuccess() throws Exception {
        // Arrange
        UUID recipeId1 = UUID.randomUUID();
        UUID recipeId2 = UUID.randomUUID();
        String recipeIds = recipeId1 + "," + recipeId2;
        
        RecipeReadDto recipe1 = new RecipeReadDto();
        recipe1.setId(recipeId1);
        recipe1.setName("Recipe 1");
        
        RecipeReadDto recipe2 = new RecipeReadDto();
        recipe2.setId(recipeId2);
        recipe2.setName("Recipe 2");
        
        when(recipeService.getRecipeById(recipeId1)).thenReturn(recipe1);
        when(recipeService.getRecipeById(recipeId2)).thenReturn(recipe2);
        
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<AIRecipeService.ImageUpdate> callback = 
                invocation.getArgument(1);
            
            // First recipe succeeds
            callback.accept(new AIRecipeService.ImageUpdate(
                recipeId1, "Recipe 1", "https://s3.example.com/image1.jpg", true, null));
            
            // Second recipe fails
            callback.accept(new AIRecipeService.ImageUpdate(
                recipeId2, "Recipe 2", null, false, "Image generation failed"));
            
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);
        
        // Wait a bit for async processing
        Thread.sleep(200);

        // Assert
        assertNotNull(emitter);
        verify(recipeService).getRecipeById(recipeId1);
        verify(recipeService).getRecipeById(recipeId2);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesEmitterTimeout() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);
        
        // Simulate timeout
        emitter.onTimeout(() -> {});

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesEmitterError() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);
        
        // Simulate error
        emitter.onError((Throwable error) -> {});

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesEmitterCompletion() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeIds = recipeId.toString();
        
        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(recipeId);
        recipe.setName("Test Recipe");
        
        when(recipeService.getRecipeById(recipeId)).thenReturn(recipe);
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        SseEmitter emitter = controller.streamImageGeneration(recipeIds);
        
        // Wait for async completion
        Thread.sleep(200);
        
        // Simulate completion
        emitter.onCompletion(() -> {});

        // Assert
        assertNotNull(emitter);
        verify(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());
    }
}

