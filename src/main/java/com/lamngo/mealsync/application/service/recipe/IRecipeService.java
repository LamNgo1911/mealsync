package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;

import java.util.List;
import java.util.UUID;

public interface IRecipeService {
    RecipeReadDto createRecipe(RecipeCreateDto recipeCreateDto);
    RecipeReadDto getRecipeById(UUID id);
    List<RecipeReadDto> getAllRecipes();
    RecipeReadDto updateRecipe(UUID id, RecipeUpdateDto recipeUpdateDto);
    void deleteRecipe(UUID id);
    void addRecipeToUser(UUID userId, UUID recipeId);
}
