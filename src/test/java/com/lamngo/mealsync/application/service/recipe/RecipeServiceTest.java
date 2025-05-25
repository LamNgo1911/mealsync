package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.*;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.mapper.UserRecipeMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeIngredientMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.IUserRecipeRepo;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipeServiceTest {
    private IRecipeRepo recipeRepo;
    private RecipeMapper recipeMapper;
    private IUserRecipeRepo userRecipeRepo;
    private UserRecipeMapper userRecipeMapper;
    private IUserRepo userRepo;
    private RecipeIngredientMapper recipeIngredientMapper;
    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeRepo = mock(IRecipeRepo.class);
        recipeMapper = mock(RecipeMapper.class);
        userRecipeRepo = mock(IUserRecipeRepo.class);
        userRecipeMapper = mock(UserRecipeMapper.class);
        userRepo = mock(IUserRepo.class);
        recipeIngredientMapper = mock(RecipeIngredientMapper.class);
        recipeService = new RecipeService(recipeRepo, recipeMapper, userRecipeRepo, userRecipeMapper, userRepo, recipeIngredientMapper);
    }

    @Test
    void createRecipe_success() {
        RecipeIngredientReadDto ingredientDto = new RecipeIngredientReadDto();
        ingredientDto.setName("Egg");
        ingredientDto.setQuantity("2");
        ingredientDto.setUnit("pcs");
        RecipeCreateDto createDto = new RecipeCreateDto();
        createDto.setIngredients(List.of(ingredientDto));

        Recipe recipe = mock(Recipe.class);
        Recipe savedRecipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeMapper.toRecipe(createDto)).thenReturn(recipe);
        when(recipeRepo.createRecipe(recipe)).thenReturn(savedRecipe);
        when(recipeMapper.toRecipeReadDto(savedRecipe)).thenReturn(readDto);

        RecipeReadDto result = recipeService.createRecipe(createDto);
        assertEquals(readDto, result);
        verify(recipeRepo).createRecipe(recipe);
    }

    @Test
    void getRecipeById_found() {
        UUID id = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.of(recipe));
        when(recipeMapper.toRecipeReadDto(recipe)).thenReturn(readDto);
        RecipeReadDto result = recipeService.getRecipeById(id);
        assertEquals(readDto, result);
    }

    @Test
    void getRecipeById_notFound() {
        UUID id = UUID.randomUUID();
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.getRecipeById(id));
    }

    @Test
    void getAllRecipes_success() {
        int limit = 10, offset = 0;
        OffsetPage page = new OffsetPage(limit, offset);
        Page<Recipe> recipePage = mock(Page.class);
        Recipe recipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeRepo.getAllRecipes(any(OffsetPage.class))).thenReturn(recipePage);
        when(recipePage.getContent()).thenReturn(List.of(recipe));
        when(recipeMapper.toRecipeReadDto(recipe)).thenReturn(readDto);
        when(recipePage.getTotalElements()).thenReturn(1L);
        when(recipePage.hasNext()).thenReturn(false);
        PaginationResponse<RecipeReadDto> resp = recipeService.getAllRecipes(limit, offset);
        assertEquals(1, resp.getData().size());
        assertEquals(readDto, resp.getData().get(0));
    }

    @Test
    void updateRecipe_success() {
        UUID id = UUID.randomUUID();
        RecipeUpdateDto updateDto = mock(RecipeUpdateDto.class);
        Recipe recipe = mock(Recipe.class);
        Recipe updatedRecipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.of(recipe));
        doNothing().when(recipeMapper).updateRecipeFromDto(updateDto, recipe);
        when(recipeRepo.createRecipe(recipe)).thenReturn(updatedRecipe);
        when(recipeMapper.toRecipeReadDto(updatedRecipe)).thenReturn(readDto);
        RecipeReadDto result = recipeService.updateRecipe(id, updateDto);
        assertEquals(readDto, result);
    }

    @Test
    void updateRecipe_notFound() {
        UUID id = UUID.randomUUID();
        RecipeUpdateDto updateDto = mock(RecipeUpdateDto.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.updateRecipe(id, updateDto));
    }

    @Test
    void deleteRecipe_success() {
        UUID id = UUID.randomUUID();
        doNothing().when(recipeRepo).deleteRecipe(id);
        recipeService.deleteRecipe(id);
        verify(recipeRepo).deleteRecipe(id);
    }

    @Test
    void addRecipeToUser_success() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        User user = mock(User.class);
        UserRecipe userRecipe = mock(UserRecipe.class);
        UserRecipeReadDto userRecipeReadDto = mock(UserRecipeReadDto.class);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipe));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRecipeRepo.saveUserRecipe(any())).thenReturn(userRecipe);
        when(userRecipeMapper.toUserRecipeReadDto(userRecipe)).thenReturn(userRecipeReadDto);
        UserRecipeReadDto result = recipeService.addRecipeToUser(userId, recipeId);
        assertEquals(userRecipeReadDto, result);
    }

    @Test
    void addRecipeToUser_recipeNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.addRecipeToUser(userId, recipeId));
    }

    @Test
    void addRecipeToUser_userNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipe));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.addRecipeToUser(userId, recipeId));
    }
}
