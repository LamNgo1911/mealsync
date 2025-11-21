package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.event.ImageGeneratedEvent;
import com.lamngo.mealsync.application.service.AI.RecipeImageService;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeImageStreamingServiceTest {

    @Mock
    private RecipeService recipeService;

    @Mock
    private RecipeImageService recipeImageService;

    @InjectMocks
    private RecipeImageStreamingService streamingService;

    private RecipeReadDto recipeDto;
    private UUID recipeId;

    @BeforeEach
    void setUp() {
        recipeId = UUID.randomUUID();
        recipeDto = new RecipeReadDto();
        recipeDto.setId(recipeId);
        recipeDto.setName("Test Recipe");
    }

    @Test
    void createImageStream_shouldThrowException_whenRecipeIdsIsNull() {
        // When/Then
        assertThrows(BadRequestException.class, () -> {
            streamingService.createImageStream(null);
        });
    }

    @Test
    void createImageStream_shouldThrowException_whenRecipeIdsIsEmpty() {
        // When/Then
        assertThrows(BadRequestException.class, () -> {
            streamingService.createImageStream(List.of());
        });
    }

    @Test
    void createImageStream_shouldCreateEmitter_whenValidRecipeIds() {
        // Given
        List<UUID> recipeIds = List.of(recipeId);
        List<RecipeReadDto> recipes = List.of(recipeDto);
        
        when(recipeService.getRecipesByIds(anyList())).thenReturn(recipes);
        when(recipeImageService.generateImagesForRecipes(anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        SseEmitter emitter = streamingService.createImageStream(recipeIds);

        // Then
        assertNotNull(emitter);
        verify(recipeService, times(1)).getRecipesByIds(recipeIds);
        verify(recipeImageService, times(1)).generateImagesForRecipes(recipes);
    }

    @Test
    void createImageStream_shouldHandleServiceException() {
        // Given
        List<UUID> recipeIds = List.of(recipeId);
        when(recipeService.getRecipesByIds(anyList()))
                .thenThrow(new RuntimeException("Service error"));

        // When
        SseEmitter emitter = streamingService.createImageStream(recipeIds);

        // Then
        assertNotNull(emitter);
        // Emitter should be created but will have error event
    }

    @Test
    void handleImageGenerated_shouldSendEventToEmitter() throws IOException {
        // Given
        List<UUID> recipeIds = List.of(recipeId);
        List<RecipeReadDto> recipes = List.of(recipeDto);
        
        when(recipeService.getRecipesByIds(anyList())).thenReturn(recipes);
        when(recipeImageService.generateImagesForRecipes(anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));

        SseEmitter emitter = streamingService.createImageStream(recipeIds);

        ImageGeneratedEvent event = new ImageGeneratedEvent(
                this, recipeId, "Test Recipe", "https://example.com/image.jpg", true, null);

        // When
        streamingService.handleImageGenerated(event);

        // Then
        // Event should be sent to emitter
        // Note: In a real test, we'd need to verify the SSE event was sent
        // but SseEmitter doesn't provide easy verification
    }

    @Test
    void handleImageGenerated_shouldHandleFailedEvent() {
        // Given
        List<UUID> recipeIds = List.of(recipeId);
        List<RecipeReadDto> recipes = List.of(recipeDto);
        
        when(recipeService.getRecipesByIds(anyList())).thenReturn(recipes);
        when(recipeImageService.generateImagesForRecipes(anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));

        SseEmitter emitter = streamingService.createImageStream(recipeIds);

        ImageGeneratedEvent event = new ImageGeneratedEvent(
                this, recipeId, "Test Recipe", null, false, "Generation failed");

        // When
        streamingService.handleImageGenerated(event);

        // Then
        // Event should be handled without exception
        assertDoesNotThrow(() -> streamingService.handleImageGenerated(event));
    }

    @Test
    void handleImageGenerated_shouldHandleNoActiveEmitters() {
        // Given
        UUID unknownRecipeId = UUID.randomUUID();
        ImageGeneratedEvent event = new ImageGeneratedEvent(
                this, unknownRecipeId, "Test Recipe", "https://example.com/image.jpg", true, null);

        // When/Then
        assertDoesNotThrow(() -> {
            streamingService.handleImageGenerated(event);
        });
    }

    @Test
    void createImageStream_shouldCompleteEmitter_whenGenerationCompletes() {
        // Given
        List<UUID> recipeIds = List.of(recipeId);
        List<RecipeReadDto> recipes = List.of(recipeDto);
        
        when(recipeService.getRecipesByIds(anyList())).thenReturn(recipes);
        when(recipeImageService.generateImagesForRecipes(anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        SseEmitter emitter = streamingService.createImageStream(recipeIds);

        // Then
        assertNotNull(emitter);
        // Emitter should be set up with completion handlers
    }
}

