package com.lamngo.mealsync.domain.repository.recipe;

import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;

import java.util.List;
import java.util.UUID;

public interface IRecipeIngredient {
    RecipeIngredient createIngredient(RecipeIngredient recipeIngredient);
    void deleteIngredientById(UUID id);
    RecipeIngredient getIngredientById(UUID id);
    List<RecipeIngredient> getAllIngredients();
    List<RecipeIngredient> getIngredientsByRecipeId(UUID recipeId);
}
