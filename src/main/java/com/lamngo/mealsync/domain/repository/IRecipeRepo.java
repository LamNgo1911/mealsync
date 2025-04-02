package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.Recipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IRecipeRepo {
    Recipe createRecipe(Recipe recipe);
    Optional<Recipe> getRecipeById(UUID id);
    List<Recipe> getAllRecipes();
    void deleteRecipe(UUID id);
}
