package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.Recipe;

import java.util.List;
import java.util.Optional;

public interface IRecipeRepo {
    Recipe createRecipe(Recipe recipe);
    Optional<Recipe> getRecipeById(String id);
    List<Recipe> getAllRecipes();
    void updateRecipe(Recipe recipe);
    void deleteRecipe(String id);
}
