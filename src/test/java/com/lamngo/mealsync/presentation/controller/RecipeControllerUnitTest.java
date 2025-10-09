package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.*;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeCreateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.service.AI.GeminiService;
import com.lamngo.mealsync.application.service.recipe.RecipeService;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipeControllerUnitTest {
    @Mock GeminiService geminiService;
    @Mock RecipeService recipeService;
    @InjectMocks RecipeController controller;

    @BeforeEach
    void setup() { MockitoAnnotations.openMocks(this); }

    @Test
    void generateRecipesFromIngredients_success() {
        GenerateRecipeRequest req = new GenerateRecipeRequest();
        req.setIngredients(List.of("egg", "milk"));
        req.setUserPreference(null);
        List<RecipeReadDto> recipes = List.of(new RecipeReadDto());
        when(geminiService.generateRecipes(anyList(), any())).thenReturn(recipes);
        ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> resp = controller.generateRecipesFromIngredients(req);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(recipes, resp.getBody().getData());
    }

    @Test
    void saveRecipeWithUser_success() {
        UserRecipeCreateDto dto = new UserRecipeCreateDto();
        dto.setUserId(UUID.randomUUID());
        dto.setRecipeId(UUID.randomUUID());
        UserRecipeReadDto readDto = new UserRecipeReadDto();
        when(recipeService.addRecipeToUser(any(), any())).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<UserRecipeReadDto>> resp = controller.saveRecipeWithUser(dto);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void createRecipe_success() {
        RecipeCreateDto dto = new RecipeCreateDto();
        RecipeReadDto readDto = new RecipeReadDto();
        when(recipeService.createRecipe(any())).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<RecipeReadDto>> resp = controller.createRecipe(dto);
        assertEquals(201, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void getRecipeById_success() {
        UUID id = UUID.randomUUID();
        RecipeReadDto readDto = new RecipeReadDto();
        when(recipeService.getRecipeById(id)).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<RecipeReadDto>> resp = controller.getRecipeById(id.toString());
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void getAllRecipes_success() {
        PaginationResponse<RecipeReadDto> page = PaginationResponse.<RecipeReadDto>builder()
                .data(List.of(new RecipeReadDto()))
                .offset(0)
                .limit(2)
                .totalElements(1L)
                .hasNext(false)
                .build();
        when(recipeService.getAllRecipes(anyInt(), anyInt())).thenReturn(page);
        ResponseEntity<PaginationResponse<RecipeReadDto>> resp = controller.getAllRecipes(0, 2, null, null, null, null, null, null);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(page, resp.getBody());
    }

    @Test
    void updateRecipe_success() {
        UUID id = UUID.randomUUID();
        RecipeUpdateDto updateDto = new RecipeUpdateDto();
        RecipeReadDto readDto = new RecipeReadDto();
        when(recipeService.updateRecipe(eq(id), any())).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<RecipeReadDto>> resp = controller.updateRecipe(id.toString(), updateDto);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void deleteRecipe_success() {
        UUID id = UUID.randomUUID();
        doNothing().when(recipeService).deleteRecipe(id);
        ResponseEntity<Void> resp = controller.deleteRecipe(id.toString());
        assertEquals(204, resp.getStatusCodeValue());
        verify(recipeService).deleteRecipe(id);
    }
}
