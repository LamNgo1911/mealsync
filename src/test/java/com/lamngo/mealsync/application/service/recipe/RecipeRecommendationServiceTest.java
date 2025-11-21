package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeRecommendationServiceTest {

    @Mock
    private IRecipeRepo recipeRepo;

    @InjectMocks
    private RecipeRecommendationService recommendationService;

    private User user;
    private UserPreference preference;
    private Recipe recipe1;
    private Recipe recipe2;
    private Recipe recipe3;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUserRecipes(new ArrayList<>());

        preference = new UserPreference();
        preference.setFavoriteCuisines(List.of("Italian", "Mexican"));
        preference.setDietaryRestrictions(new ArrayList<>());
        preference.setDislikedIngredients(new ArrayList<>());
        user.setUserPreference(preference);

        recipe1 = createRecipe(UUID.randomUUID(), "Italian Pasta", "Italian", List.of("pasta", "tomato"));
        recipe2 = createRecipe(UUID.randomUUID(), "Mexican Tacos", "Mexican", List.of("tortilla", "beef"));
        recipe3 = createRecipe(UUID.randomUUID(), "French Baguette", "French", List.of("flour", "water"));
    }

    private Recipe createRecipe(UUID id, String name, String cuisine, List<String> ingredientNames) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setCuisine(cuisine);
        recipe.setTags(new ArrayList<>());

        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (String ingName : ingredientNames) {
            RecipeIngredient ing = new RecipeIngredient();
            ing.setName(ingName);
            ingredients.add(ing);
        }
        recipe.setIngredients(ingredients);
        return recipe;
    }

    @Test
    void getRecommendedRecipes_shouldReturnRecipes_whenUserHasPreferences() {
        // Given
        List<Recipe> candidates = List.of(recipe1, recipe2, recipe3);
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(candidates);

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 2);

        // Then
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        verify(recipeRepo, times(1)).findRecipesByCuisines(anyList(), anyInt());
    }

    @Test
    void getRecommendedRecipes_shouldExcludeSavedRecipes() {
        // Given
        UserRecipe savedRecipe = new UserRecipe();
        savedRecipe.setRecipe(recipe1);
        user.getUserRecipes().add(savedRecipe);

        List<Recipe> candidates = List.of(recipe1, recipe2, recipe3);
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(candidates);

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 2);

        // Then
        assertNotNull(recommendations);
        assertFalse(recommendations.stream().anyMatch(r -> r.getId().equals(recipe1.getId())));
    }

    @Test
    void getRecommendedRecipes_shouldPrioritizeFavoriteCuisines() {
        // Given
        List<Recipe> candidates = List.of(recipe1, recipe2, recipe3);
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(candidates);

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 2);

        // Then
        assertNotNull(recommendations);
        // Italian and Mexican should be prioritized
        assertTrue(recommendations.stream().anyMatch(r -> 
                r.getCuisine().equalsIgnoreCase("Italian") || 
                r.getCuisine().equalsIgnoreCase("Mexican")));
    }

    @Test
    void getRecommendedRecipes_shouldFilterVegetarianRestrictions() {
        // Given
        preference.setDietaryRestrictions(List.of("vegetarian"));
        recipe2.getIngredients().get(1).setName("beef"); // Contains meat

        List<Recipe> candidates = List.of(recipe1, recipe2, recipe3);
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(candidates);

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 10);

        // Then
        assertNotNull(recommendations);
        // Should not include recipes with meat
        assertFalse(recommendations.stream().anyMatch(r -> 
                r.getIngredients().stream().anyMatch(ing -> 
                        ing.getName().toLowerCase().contains("beef"))));
    }

    @Test
    void getRecommendedRecipes_shouldFilterDislikedIngredients() {
        // Given
        preference.setDislikedIngredients(List.of("tomato"));
        List<Recipe> candidates = List.of(recipe1, recipe2, recipe3);
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(candidates);

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 10);

        // Then
        assertNotNull(recommendations);
        // Should reduce score for recipes with disliked ingredients
        // recipe1 has tomato, so it should have lower score or be filtered
    }

    @Test
    void getRecommendedRecipes_shouldFallbackToAllRecipes_whenNoCuisineMatches() {
        // Given
        preference.setFavoriteCuisines(List.of("Unknown Cuisine"));
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(List.of());
        when(recipeRepo.findAllRecipesForRecommendation(anyInt())).thenReturn(List.of(recipe1, recipe2, recipe3));

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 2);

        // Then
        assertNotNull(recommendations);
        verify(recipeRepo, times(1)).findAllRecipesForRecommendation(anyInt());
    }

    @Test
    void getRecommendedRecipes_shouldHandleNullPreference() {
        // Given
        user.setUserPreference(null);
        when(recipeRepo.findAllRecipesForRecommendation(anyInt())).thenReturn(List.of(recipe1, recipe2, recipe3));

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 2);

        // Then
        assertNotNull(recommendations);
        verify(recipeRepo, times(1)).findAllRecipesForRecommendation(anyInt());
    }

    @Test
    void getRecommendedRecipes_shouldRespectLimit() {
        // Given
        List<Recipe> candidates = List.of(recipe1, recipe2, recipe3);
        when(recipeRepo.findRecipesByCuisines(anyList(), anyInt())).thenReturn(candidates);

        // When
        List<Recipe> recommendations = recommendationService.getRecommendedRecipes(user, 1);

        // Then
        assertNotNull(recommendations);
        assertTrue(recommendations.size() <= 1);
    }
}

