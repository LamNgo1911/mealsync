package com.lamngo.mealsync.integration.recipe;

import com.lamngo.mealsync.integration.BaseIntegrationTest;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Server-Sent Events (SSE) streaming endpoint.
 * Tests the complete flow from HTTP request to SSE event streaming.
 */
class RecipeStreamingIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private IRecipeRepo recipeRepo;
    
    @MockBean
    private AIRecipeService aiRecipeService;
    
    private String authToken;
    private Recipe recipe1;
    private Recipe recipe2;
    private Recipe recipe3;

    @BeforeEach
    void setUp() throws Exception {
        // Create authenticated user and get token
        authToken = createUserAndGetToken("streaming@test.com", "password123", "Streaming Test User");
        
        // Create test recipes in database
        recipe1 = createTestRecipe("Chicken Stir Fry", "A delicious stir fry");
        recipe2 = createTestRecipe("Beef Tacos", "Tasty Mexican dish");
        recipe3 = createTestRecipe("Pasta Carbonara", "Italian classic");
        
        recipe1 = recipeRepo.saveRecipe(recipe1);
        recipe2 = recipeRepo.saveRecipe(recipe2);
        recipe3 = recipeRepo.saveRecipe(recipe3);
    }

    private Recipe createTestRecipe(String name, String description) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setDescription(description);
        recipe.setCuisine("Test");
        recipe.setDifficulty("EASY");
        recipe.setPreparationTime(10);
        recipe.setCookingTime(20);
        recipe.setTotalTime(30);
        recipe.setServings(4);
        recipe.setCalories(500.0);
        recipe.setProtein(30.0);
        recipe.setCarbohydrates(40.0);
        recipe.setFat(20.0);
        return recipe;
    }

    @Test
    void streamImageGeneration_success_singleRecipe() throws Exception {
        // Arrange
        String recipeIds = recipe1.getId().toString();
        String imageUrl = "https://s3.example.com/chicken-stir-fry.jpg";
        
        RecipeReadDto recipeDto = new RecipeReadDto();
        recipeDto.setId(recipe1.getId());
        recipeDto.setName("Chicken Stir Fry");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        doAnswer(invocation -> {
            AIRecipeService.ImageUpdate update = new AIRecipeService.ImageUpdate(
                recipe1.getId(), 
                "Chicken Stir Fry", 
                imageUrl, 
                true, 
                null
            );
            @SuppressWarnings("unchecked")
            Consumer<AIRecipeService.ImageUpdate> callback = invocation.getArgument(1);
            callback.accept(update);
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());

        // Act & Assert
        MvcResult result = mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", recipeIds)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(request().asyncStarted())
            .andReturn();

        // Wait for async processing
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify the service was called
        verify(aiRecipeService, timeout(2000)).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_success_multipleRecipes() throws Exception {
        // Arrange
        String recipeIds = recipe1.getId() + "," + recipe2.getId() + "," + recipe3.getId();
        
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger callbackCount = new AtomicInteger(0);
        
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<AIRecipeService.ImageUpdate> callback = invocation.getArgument(1);
            
            // Simulate 3 image updates
            callback.accept(new AIRecipeService.ImageUpdate(
                recipe1.getId(), "Chicken Stir Fry", "https://s3.example.com/image1.jpg", true, null));
            callback.accept(new AIRecipeService.ImageUpdate(
                recipe2.getId(), "Beef Tacos", "https://s3.example.com/image2.jpg", true, null));
            callback.accept(new AIRecipeService.ImageUpdate(
                recipe3.getId(), "Pasta Carbonara", "https://s3.example.com/image3.jpg", true, null));
            
            callbackCount.set(3);
            latch.countDown();
            latch.countDown();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());

        // Act & Assert
        mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", recipeIds)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(request().asyncStarted())
            .andReturn();

        // Wait for all callbacks
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify the service was called
        verify(aiRecipeService, timeout(2000)).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_unauthorized_returns401() throws Exception {
        // Arrange
        String recipeIds = recipe1.getId().toString();

        // Act & Assert
        mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", recipeIds))
            .andExpect(status().isUnauthorized());
        
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_missingRecipeIds_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/recipes/image-stream")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isBadRequest());
        
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_invalidUUID_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", "invalid-uuid")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isBadRequest());
        
        verify(aiRecipeService, never()).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesImageGenerationFailure() throws Exception {
        // Arrange
        String recipeIds = recipe1.getId().toString();
        
        RuntimeException error = new RuntimeException("Image generation failed");
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.failedFuture(error));

        // Act & Assert
        MvcResult result = mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", recipeIds)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(request().asyncStarted())
            .andReturn();

        // Wait a bit for async processing
        Thread.sleep(500);
        
        // Verify the service was called
        verify(aiRecipeService, timeout(2000)).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_handlesPartialFailure() throws Exception {
        // Arrange
        String recipeIds = recipe1.getId() + "," + recipe2.getId();
        
        CountDownLatch latch = new CountDownLatch(2);
        
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<AIRecipeService.ImageUpdate> callback = invocation.getArgument(1);
            
            // First recipe succeeds
            callback.accept(new AIRecipeService.ImageUpdate(
                recipe1.getId(), "Chicken Stir Fry", "https://s3.example.com/image1.jpg", true, null));
            latch.countDown();
            
            // Second recipe fails
            callback.accept(new AIRecipeService.ImageUpdate(
                recipe2.getId(), "Beef Tacos", null, false, "Image generation failed"));
            latch.countDown();
            
            return CompletableFuture.completedFuture(null);
        }).when(aiRecipeService).addImagesToRecipesBatchAsync(anyList(), any());

        // Act & Assert
        mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", recipeIds)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(request().asyncStarted())
            .andReturn();

        // Wait for callbacks
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify the service was called
        verify(aiRecipeService, timeout(2000)).addImagesToRecipesBatchAsync(anyList(), any());
    }

    @Test
    void streamImageGeneration_withWhitespaceInIds_parsesCorrectly() throws Exception {
        // Arrange
        String recipeIds = recipe1.getId() + " , " + recipe2.getId(); // With spaces
        
        when(aiRecipeService.addImagesToRecipesBatchAsync(anyList(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Act & Assert
        mockMvc.perform(get("/api/v1/recipes/image-stream")
                .param("recipeIds", recipeIds)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(request().asyncStarted())
            .andReturn();

        // Wait a bit for async processing
        Thread.sleep(200);
        
        // Verify the service was called
        verify(aiRecipeService, timeout(2000)).addImagesToRecipesBatchAsync(anyList(), any());
    }
}

