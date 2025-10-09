package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.application.shared.PaginationResponse;

import java.util.List;
import java.util.UUID;

public interface IRecipeService {
    RecipeReadDto createRecipe(RecipeCreateDto recipeCreateDto);
    RecipeReadDto getRecipeById(UUID id);
    PaginationResponse<RecipeReadDto> getAllRecipes(int limit, int offset);
    PaginationResponse<RecipeReadDto> getAllRecipes(int limit, int offset, List<String> cuisines, List<String> tags,
                                                     List<String> ingredients, String difficulty,
                                                     Integer maxTotalTime, Integer minServings);
    RecipeReadDto updateRecipe(UUID id, RecipeUpdateDto recipeUpdateDto);
    void deleteRecipe(UUID id);
    UserRecipeReadDto addRecipeToUser(UUID userId, UUID recipeId);
    List<RecipeReadDto> getRecommendedRecipes(UUID userId, int limit);
    List<UserRecipeReadDto> getSavedRecipesByUserId(UUID userId);
}
