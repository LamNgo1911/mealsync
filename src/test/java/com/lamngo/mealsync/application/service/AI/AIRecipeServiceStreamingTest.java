package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIRecipeServiceStreamingTest {
    @Mock
    private IRecipeRepo recipeRepo;
    
    @Mock
    private ImageGeneratorService imageGeneratorService;
    
    @Mock
    private S3Service s3Service;
    
    @InjectMocks
    private AIRecipeService aiRecipeService;

    private RecipeReadDto createRecipeDto(UUID id, String name) {
        RecipeReadDto dto = new RecipeReadDto();
        dto.setId(id);
        dto.setName(name);
        dto.setImageUrl(null); // No image initially
        return dto;
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_invokesCallbackOnSuccess() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String imageUrl = "https://s3.example.com/test-recipe.jpg";
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Test Recipe", base64Image);
        
        Recipe recipeEntity = new Recipe();
        recipeEntity.setId(recipeId);
        recipeEntity.setName("Test Recipe");
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), eq("Test Recipe"))).thenReturn(imageUrl);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipeEntity));
        when(recipeRepo.saveRecipe(any(Recipe.class))).thenReturn(recipeEntity);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicReference<AIRecipeService.ImageUpdate> capturedUpdate = new AtomicReference<>();
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            capturedUpdate.set(update);
            callbackCount.incrementAndGet();
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get(); // Wait for completion

        // Assert
        assertEquals(1, callbackCount.get());
        assertNotNull(capturedUpdate.get());
        assertEquals(recipeId, capturedUpdate.get().getRecipeId());
        assertEquals("Test Recipe", capturedUpdate.get().getRecipeName());
        assertEquals(imageUrl, capturedUpdate.get().getImageUrl());
        assertTrue(capturedUpdate.get().isSuccess());
        assertNull(capturedUpdate.get().getErrorMessage());
        
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service).uploadImage(any(byte[].class), eq("Test Recipe"));
        verify(recipeRepo).saveRecipe(any(Recipe.class));
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_invokesCallbackOnMultipleRecipes() throws Exception {
        // Arrange
        UUID recipeId1 = UUID.randomUUID();
        UUID recipeId2 = UUID.randomUUID();
        UUID recipeId3 = UUID.randomUUID();
        
        RecipeReadDto recipe1 = createRecipeDto(recipeId1, "Recipe 1");
        RecipeReadDto recipe2 = createRecipeDto(recipeId2, "Recipe 2");
        RecipeReadDto recipe3 = createRecipeDto(recipeId3, "Recipe 3");
        List<RecipeReadDto> recipes = List.of(recipe1, recipe2, recipe3);
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Recipe 1", "base64image1");
        imageMap.put("Recipe 2", "base64image2");
        imageMap.put("Recipe 3", "base64image3");
        
        Recipe recipeEntity1 = new Recipe();
        recipeEntity1.setId(recipeId1);
        Recipe recipeEntity2 = new Recipe();
        recipeEntity2.setId(recipeId2);
        Recipe recipeEntity3 = new Recipe();
        recipeEntity3.setId(recipeId3);
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), anyString()))
            .thenReturn("https://s3.example.com/image1.jpg")
            .thenReturn("https://s3.example.com/image2.jpg")
            .thenReturn("https://s3.example.com/image3.jpg");
        when(recipeRepo.getRecipeById(recipeId1)).thenReturn(Optional.of(recipeEntity1));
        when(recipeRepo.getRecipeById(recipeId2)).thenReturn(Optional.of(recipeEntity2));
        when(recipeRepo.getRecipeById(recipeId3)).thenReturn(Optional.of(recipeEntity3));
        when(recipeRepo.saveRecipe(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger callbackCount = new AtomicInteger(0);
        List<AIRecipeService.ImageUpdate> capturedUpdates = Collections.synchronizedList(new ArrayList<>());
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            capturedUpdates.add(update);
            callbackCount.incrementAndGet();
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for all callbacks
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get();

        // Assert
        assertEquals(3, callbackCount.get());
        assertEquals(3, capturedUpdates.size());
        
        Set<UUID> receivedIds = new HashSet<>();
        for (AIRecipeService.ImageUpdate update : capturedUpdates) {
            assertTrue(update.isSuccess());
            assertNotNull(update.getImageUrl());
            receivedIds.add(update.getRecipeId());
        }
        
        assertTrue(receivedIds.contains(recipeId1));
        assertTrue(receivedIds.contains(recipeId2));
        assertTrue(receivedIds.contains(recipeId3));
        
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service, times(3)).uploadImage(any(byte[].class), anyString());
        verify(recipeRepo, times(3)).saveRecipe(any(Recipe.class));
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_invokesCallbackOnFailure() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Test Recipe", "invalid-base64");
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIRecipeService.ImageUpdate> capturedUpdate = new AtomicReference<>();
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            capturedUpdate.set(update);
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get();

        // Assert
        assertNotNull(capturedUpdate.get());
        assertEquals(recipeId, capturedUpdate.get().getRecipeId());
        assertFalse(capturedUpdate.get().isSuccess());
        assertNotNull(capturedUpdate.get().getErrorMessage());
        
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service, never()).uploadImage(any(byte[].class), anyString());
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_handlesMissingImageInBatch() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        Map<String, String> imageMap = new HashMap<>(); // Empty map - no image generated
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIRecipeService.ImageUpdate> capturedUpdate = new AtomicReference<>();
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            capturedUpdate.set(update);
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get();

        // Assert
        assertNotNull(capturedUpdate.get());
        assertEquals(recipeId, capturedUpdate.get().getRecipeId());
        assertFalse(capturedUpdate.get().isSuccess());
        assertEquals("No image generated", capturedUpdate.get().getErrorMessage());
        
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service, never()).uploadImage(any(byte[].class), anyString());
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_handlesRecipeNotFound() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String imageUrl = "https://s3.example.com/test-recipe.jpg";
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Test Recipe", base64Image);
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), eq("Test Recipe"))).thenReturn(imageUrl);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.empty()); // Recipe not found
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIRecipeService.ImageUpdate> capturedUpdate = new AtomicReference<>();
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            capturedUpdate.set(update);
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get();

        // Assert
        assertNotNull(capturedUpdate.get());
        assertEquals(recipeId, capturedUpdate.get().getRecipeId());
        assertFalse(capturedUpdate.get().isSuccess());
        assertEquals("Recipe not found", capturedUpdate.get().getErrorMessage());
        
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service).uploadImage(any(byte[].class), eq("Test Recipe"));
        verify(recipeRepo).getRecipeById(recipeId);
        verify(recipeRepo, never()).saveRecipe(any(Recipe.class));
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_handlesS3UploadFailure() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Test Recipe", base64Image);
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), eq("Test Recipe"))).thenReturn(null); // S3 upload failed
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AIRecipeService.ImageUpdate> capturedUpdate = new AtomicReference<>();
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            capturedUpdate.set(update);
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get();

        // Assert
        assertNotNull(capturedUpdate.get());
        assertEquals(recipeId, capturedUpdate.get().getRecipeId());
        assertFalse(capturedUpdate.get().isSuccess());
        assertEquals("S3 upload failed", capturedUpdate.get().getErrorMessage());
        
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service).uploadImage(any(byte[].class), eq("Test Recipe"));
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_skipsRecipesWithExistingImages() throws Exception {
        // Arrange
        UUID recipeId1 = UUID.randomUUID();
        UUID recipeId2 = UUID.randomUUID();
        
        RecipeReadDto recipe1 = createRecipeDto(recipeId1, "Recipe 1");
        recipe1.setImageUrl("https://s3.example.com/existing.jpg"); // Already has image
        
        RecipeReadDto recipe2 = createRecipeDto(recipeId2, "Recipe 2");
        recipe2.setImageUrl(null); // Needs image
        
        List<RecipeReadDto> recipes = List.of(recipe1, recipe2);
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Recipe 2", "base64image");
        
        Recipe recipeEntity2 = new Recipe();
        recipeEntity2.setId(recipeId2);
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), eq("Recipe 2"))).thenReturn("https://s3.example.com/image2.jpg");
        when(recipeRepo.getRecipeById(recipeId2)).thenReturn(Optional.of(recipeEntity2));
        when(recipeRepo.saveRecipe(any(Recipe.class))).thenReturn(recipeEntity2);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger(0);
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            callbackCount.incrementAndGet();
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, callback);
        
        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get();

        // Assert
        assertEquals(1, callbackCount.get()); // Only one callback for recipe2
        verify(imageGeneratorService).generateImagesBatch(anyList());
        // Verify only recipe2 was processed
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(imageGeneratorService).generateImagesBatch(captor.capture());
        List<ImageGeneratorService.ImageGenerationRequest> requests = captor.getValue();
        assertEquals(1, requests.size());
        assertEquals("Recipe 2", requests.get(0).getRecipeName());
    }

    @Test
    void addImagesToRecipesBatchAsync_withoutCallback_worksCorrectly() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String imageUrl = "https://s3.example.com/test-recipe.jpg";
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Test Recipe", base64Image);
        
        Recipe recipeEntity = new Recipe();
        recipeEntity.setId(recipeId);
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), eq("Test Recipe"))).thenReturn(imageUrl);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipeEntity));
        when(recipeRepo.saveRecipe(any(Recipe.class))).thenReturn(recipeEntity);

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes);
        future.get(); // Wait for completion

        // Assert
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service).uploadImage(any(byte[].class), eq("Test Recipe"));
        verify(recipeRepo).saveRecipe(any(Recipe.class));
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_handlesEmptyRecipeList() throws Exception {
        // Arrange
        List<RecipeReadDto> emptyRecipes = List.of();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger(0);
        
        Consumer<AIRecipeService.ImageUpdate> callback = update -> {
            callbackCount.incrementAndGet();
            latch.countDown();
        };

        // Act
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(emptyRecipes, callback);
        future.get();

        // Assert
        assertEquals(0, callbackCount.get()); // No callbacks for empty list
        verify(imageGeneratorService, never()).generateImagesBatch(anyList());
        verify(s3Service, never()).uploadImage(any(byte[].class), anyString());
    }

    @Test
    void addImagesToRecipesBatchAsync_withCallback_handlesNullCallback() throws Exception {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        RecipeReadDto recipe = createRecipeDto(recipeId, "Test Recipe");
        List<RecipeReadDto> recipes = List.of(recipe);
        
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String imageUrl = "https://s3.example.com/test-recipe.jpg";
        
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("Test Recipe", base64Image);
        
        Recipe recipeEntity = new Recipe();
        recipeEntity.setId(recipeId);
        
        when(imageGeneratorService.generateImagesBatch(anyList())).thenReturn(imageMap);
        when(s3Service.uploadImage(any(byte[].class), eq("Test Recipe"))).thenReturn(imageUrl);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipeEntity));
        when(recipeRepo.saveRecipe(any(Recipe.class))).thenReturn(recipeEntity);

        // Act - Pass null callback
        CompletableFuture<Void> future = aiRecipeService.addImagesToRecipesBatchAsync(recipes, null);
        future.get();

        // Assert - Should not throw exception
        verify(imageGeneratorService).generateImagesBatch(anyList());
        verify(s3Service).uploadImage(any(byte[].class), eq("Test Recipe"));
        verify(recipeRepo).saveRecipe(any(Recipe.class));
    }

    @Test
    void imageUpdate_constructorAndGetters_workCorrectly() {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeName = "Test Recipe";
        String imageUrl = "https://s3.example.com/image.jpg";
        boolean success = true;
        String errorMessage = null;

        // Act
        AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
            recipeId, recipeName, imageUrl, success, errorMessage);

        // Assert
        assertEquals(recipeId, update.getRecipeId());
        assertEquals(recipeName, update.getRecipeName());
        assertEquals(imageUrl, update.getImageUrl());
        assertEquals(success, update.isSuccess());
        assertEquals(errorMessage, update.getErrorMessage());
    }

    @Test
    void imageUpdate_withError_workCorrectly() {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        String recipeName = "Test Recipe";
        String imageUrl = null;
        boolean success = false;
        String errorMessage = "Image generation failed";

        // Act
        AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
            recipeId, recipeName, imageUrl, success, errorMessage);

        // Assert
        assertEquals(recipeId, update.getRecipeId());
        assertEquals(recipeName, update.getRecipeName());
        assertNull(update.getImageUrl());
        assertFalse(update.isSuccess());
        assertEquals(errorMessage, update.getErrorMessage());
    }
}

