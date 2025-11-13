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
    Page<Recipe> getAllRecipesFiltered(OffsetPage pageable, String name, List<String> cuisines, List<String> tags,
                                        List<String> ingredients, String difficulty,
                                        Integer maxTotalTime, Integer minServings);
    void deleteRecipe(UUID id);
    Optional<Recipe> findByIngredientKey(String ingredientKey);
    long countAllRecipes();
    List<Recipe> findRecipesByCuisines(List<String> cuisines, int limit);
    List<Recipe> findAllRecipesForRecommendation(int limit);
    
    /**
     * Saves a recipe to the database. Can be used for both creating and updating.
     * @param recipe The recipe to save
     * @return The saved recipe
     */
    Recipe saveRecipe(Recipe recipe);
    
    /**
     * Finds all recipes for similarity checking
     * @return List of all recipes
     */
    List<Recipe> findAllRecipes();
}
