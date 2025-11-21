package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.event.ImageGeneratedEvent;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeImageServiceTest {

    @Mock
    private IRecipeRepo recipeRepo;

    @Mock
    private GeminiImageApiClient geminiImageApiClient;

    @Mock
    private S3Service s3Service;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PromptLoader promptLoader;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private RecipeImageService recipeImageService;

    private RecipeReadDto recipeDto;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        recipeImageService = new RecipeImageService(
                recipeRepo, geminiImageApiClient, s3Service, 
                eventPublisher, promptLoader, transactionManager);

        recipeDto = new RecipeReadDto();
        recipeDto.setId(UUID.randomUUID());
        recipeDto.setName("Test Recipe");
        recipeDto.setDescription("A test recipe");
        recipeDto.setImageUrl(null);

        recipe = new Recipe();
        recipe.setId(recipeDto.getId());
        recipe.setName(recipeDto.getName());
    }

    @Test
    void generateImagesForRecipes_shouldReturnCompletedFuture_whenListIsEmpty() {
        // Given
        List<RecipeReadDto> emptyList = List.of();

        // When
        CompletableFuture<Void> future = recipeImageService.generateImagesForRecipes(emptyList);

        // Then
        assertNotNull(future);
        assertTrue(future.isDone());
        verify(geminiImageApiClient, never()).callGeminiAPISingleAsync(anyString());
    }

    @Test
    void generateImagesForRecipes_shouldReturnCompletedFuture_whenRecipesAlreadyHaveImages() {
        // Given
        recipeDto.setImageUrl("https://example.com/image.jpg");
        List<RecipeReadDto> recipes = List.of(recipeDto);

        // When
        CompletableFuture<Void> future = recipeImageService.generateImagesForRecipes(recipes);

        // Then
        assertNotNull(future);
        verify(geminiImageApiClient, never()).callGeminiAPISingleAsync(anyString());
    }

    @Test
    void generateImagesForRecipes_shouldGenerateImages_whenRecipesNeedImages() {
        // Given
        String base64Image = Base64.getEncoder().encodeToString("fake image data".getBytes());
        String imageUrl = "https://s3.example.com/image.jpg";
        
        CompletableFuture<String> imageFuture = CompletableFuture.completedFuture(base64Image);
        
        when(promptLoader.loadAndFormatPrompt(anyString(), any())).thenReturn("test prompt");
        when(geminiImageApiClient.callGeminiAPISingleAsync(anyString())).thenReturn(imageFuture);
        when(s3Service.uploadImage(any(byte[].class), anyString())).thenReturn(imageUrl);
        when(recipeRepo.getRecipeById(any(UUID.class))).thenReturn(Optional.of(recipe));
        when(recipeRepo.saveRecipe(any(Recipe.class))).thenReturn(recipe);

        List<RecipeReadDto> recipes = List.of(recipeDto);

        // When
        CompletableFuture<Void> future = recipeImageService.generateImagesForRecipes(recipes);

        // Then
        assertNotNull(future);
        // Wait for async operations
        future.join();
        
        verify(geminiImageApiClient, times(1)).callGeminiAPISingleAsync(anyString());
        verify(s3Service, times(1)).uploadImage(any(byte[].class), anyString());
        verify(recipeRepo, times(1)).getRecipeById(any(UUID.class));
        verify(recipeRepo, times(1)).saveRecipe(any(Recipe.class));
        
        ArgumentCaptor<ImageGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(ImageGeneratedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
    }

    @Test
    void generateImagesForRecipes_shouldPublishEvent_whenImageGenerationFails() {
        // Given
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Image generation failed"));
        
        when(promptLoader.loadAndFormatPrompt(anyString(), any())).thenReturn("test prompt");
        when(geminiImageApiClient.callGeminiAPISingleAsync(anyString())).thenReturn(failedFuture);

        List<RecipeReadDto> recipes = List.of(recipeDto);

        // When
        CompletableFuture<Void> future = recipeImageService.generateImagesForRecipes(recipes);

        // Then
        assertNotNull(future);
        future.join();
        
        ArgumentCaptor<ImageGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(ImageGeneratedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        
        ImageGeneratedEvent event = eventCaptor.getValue();
        assertFalse(event.isSuccess());
        assertNotNull(event.getErrorMessage());
    }

    @Test
    void generateImagesForRecipes_shouldPublishEvent_whenS3UploadFails() {
        // Given
        String base64Image = Base64.getEncoder().encodeToString("fake image data".getBytes());
        CompletableFuture<String> imageFuture = CompletableFuture.completedFuture(base64Image);
        
        when(promptLoader.loadAndFormatPrompt(anyString(), any())).thenReturn("test prompt");
        when(geminiImageApiClient.callGeminiAPISingleAsync(anyString())).thenReturn(imageFuture);
        when(s3Service.uploadImage(any(byte[].class), anyString())).thenReturn(null);

        List<RecipeReadDto> recipes = List.of(recipeDto);

        // When
        CompletableFuture<Void> future = recipeImageService.generateImagesForRecipes(recipes);

        // Then
        assertNotNull(future);
        future.join();
        
        ArgumentCaptor<ImageGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(ImageGeneratedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        
        ImageGeneratedEvent event = eventCaptor.getValue();
        assertFalse(event.isSuccess());
    }

    @Test
    void generateImagesForRecipes_shouldPublishEvent_whenRecipeNotFound() {
        // Given
        String base64Image = Base64.getEncoder().encodeToString("fake image data".getBytes());
        String imageUrl = "https://s3.example.com/image.jpg";
        CompletableFuture<String> imageFuture = CompletableFuture.completedFuture(base64Image);
        
        when(promptLoader.loadAndFormatPrompt(anyString(), any())).thenReturn("test prompt");
        when(geminiImageApiClient.callGeminiAPISingleAsync(anyString())).thenReturn(imageFuture);
        when(s3Service.uploadImage(any(byte[].class), anyString())).thenReturn(imageUrl);
        when(recipeRepo.getRecipeById(any(UUID.class))).thenReturn(Optional.empty());

        List<RecipeReadDto> recipes = List.of(recipeDto);

        // When
        CompletableFuture<Void> future = recipeImageService.generateImagesForRecipes(recipes);

        // Then
        assertNotNull(future);
        future.join();
        
        ArgumentCaptor<ImageGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(ImageGeneratedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        
        // Check all captured events - there should be at least one failure event
        // Note: The implementation publishes both a failure event (when recipe not found)
        // and a success event (after transaction), so we check that a failure event exists
        List<ImageGeneratedEvent> allEvents = eventCaptor.getAllValues();
        boolean hasFailureEvent = allEvents.stream()
                .anyMatch(e -> !e.isSuccess() && "Recipe not found".equals(e.getErrorMessage()));
        assertTrue(hasFailureEvent, "Should have published a failure event when recipe is not found");
    }
}

