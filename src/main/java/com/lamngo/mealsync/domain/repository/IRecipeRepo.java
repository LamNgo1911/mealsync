package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.Recipe;

import java.util.List;

public interface IRecipeRepo {
    Recipe createRecipe(Recipe recipe);
    Recipe getRecipeById(String id);
    List<Recipe> getAllRecipes();
    void updateRecipe(Recipe recipe);
    void deleteRecipe(String id);
}
