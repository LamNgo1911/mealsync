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
    
    /**
     * Fetches multiple recipes by their IDs in a single batch query.
     * This is more efficient than calling getRecipeById() multiple times.
     * @param ids List of recipe IDs to fetch
     * @return List of recipes matching the provided IDs
     */
    List<Recipe> getRecipesByIds(List<UUID> ids);
    
    /**
     * Finds recipes with similar names using PostgreSQL trigram similarity.
     * Much faster than loading all recipes and calculating similarity in Java.
     * Requires pg_trgm extension to be enabled in PostgreSQL.
     * @param recipeName Recipe name to match
     * @param similarityThreshold Minimum similarity (0.0-1.0), typically 0.7-0.9
     * @return Optional recipe if similarity >= threshold
     */
    Optional<Recipe> findSimilarRecipeByName(String recipeName, double similarityThreshold);
    
    /**
     * Batch lookup recipes by ingredient keys.
     * More efficient than calling findByIngredientKey() multiple times.
     * @param ingredientKeys List of ingredient keys to lookup
     * @return Map of ingredientKey to Optional<Recipe>
     */
    java.util.Map<String, Optional<Recipe>> findByIngredientKeysBatch(List<String> ingredientKeys);
    
    /**
     * Batch save multiple recipes in a single transaction.
     * More efficient than calling createRecipe() multiple times.
     * @param recipes List of recipes to save
     * @return List of saved recipes
     */
    List<Recipe> saveAllRecipes(List<Recipe> recipes);
}
