package com.lamngo.mealsync.domain.repository.recipe;

import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IRecipeRepo {
    Recipe createRecipe(Recipe recipe);
    Optional<Recipe> getRecipeById(UUID id);
    Page<Recipe> getAllRecipes(OffsetPage pageable);
    void deleteRecipe(UUID id);
    Optional<Recipe> findByIngredientKey(String ingredientKey);
    long countAllRecipes();
    List<Recipe> findRecipesByCuisines(List<String> cuisines, int limit);
    List<Recipe> findAllRecipesForRecommendation(int limit);
}
