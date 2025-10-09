package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeRecommendationService {

    private final IRecipeRepo recipeRepo;

    public RecipeRecommendationService(IRecipeRepo recipeRepo) {
        this.recipeRepo = recipeRepo;
    }

    public List<Recipe> getRecommendedRecipes(User user, int limit) {
        UserPreference preference = user.getUserPreference();

        // Get candidate recipes - prioritize user's favorite cuisines
        List<Recipe> candidates = getCandidateRecipes(preference, limit * 3);

        // Get user's saved recipe IDs to avoid duplication
        Set<UUID> savedRecipeIds = user.getUserRecipes().stream()
                .map(ur -> ur.getRecipe().getId())
                .collect(Collectors.toSet());

        // Score and filter recipes
        List<ScoredRecipe> scoredRecipes = candidates.stream()
                .filter(recipe -> !savedRecipeIds.contains(recipe.getId()))
                .map(recipe -> new ScoredRecipe(recipe, calculateScore(recipe, preference, user)))
                .filter(sr -> sr.score > 0) // Only include recipes with positive scores
                .sorted(Comparator.comparingDouble(ScoredRecipe::getScore).reversed())
                .limit(limit)
                .toList();

        return scoredRecipes.stream()
                .map(ScoredRecipe::getRecipe)
                .toList();
    }

    private List<Recipe> getCandidateRecipes(UserPreference preference, int limit) {
        if (preference != null && !CollectionUtils.isEmpty(preference.getFavoriteCuisines())) {
            List<Recipe> byCuisine = recipeRepo.findRecipesByCuisines(preference.getFavoriteCuisines(), limit);
            if (!byCuisine.isEmpty()) {
                return byCuisine;
            }
        }
        // Fallback to all recipes if no cuisine preference or no matches
        return recipeRepo.findAllRecipesForRecommendation(limit);
    }

    private double calculateScore(Recipe recipe, UserPreference preference, User user) {
        double score = 0.0;

        if (preference == null) {
            return 50.0; // Neutral score if no preferences
        }

        // 1. Cuisine Match (30 points)
        score += calculateCuisineScore(recipe, preference);

        // 2. Dietary Restrictions (40 points - CRITICAL)
        double dietaryScore = calculateDietaryScore(recipe, preference);
        if (dietaryScore == 0) {
            return 0; // Recipe violates dietary restrictions
        }
        score += dietaryScore;

        // 3. Disliked Ingredients (20 points)
        score += calculateDislikedIngredientsScore(recipe, preference);

        // 4. User History Bonus (10 points)
        score += calculateUserHistoryScore(recipe, user);

        return score;
    }

    private double calculateCuisineScore(Recipe recipe, UserPreference preference) {
        if (CollectionUtils.isEmpty(preference.getFavoriteCuisines())) {
            return 15.0; // Neutral score
        }

        String recipeCuisine = recipe.getCuisine().toLowerCase();
        for (String favoriteCuisine : preference.getFavoriteCuisines()) {
            if (recipeCuisine.equals(favoriteCuisine.toLowerCase())) {
                return 30.0; // Perfect match
            }
            if (recipeCuisine.contains(favoriteCuisine.toLowerCase()) ||
                favoriteCuisine.toLowerCase().contains(recipeCuisine)) {
                return 20.0; // Partial match
            }
        }
        return 5.0; // No match
    }

    private double calculateDietaryScore(Recipe recipe, UserPreference preference) {
        if (CollectionUtils.isEmpty(preference.getDietaryRestrictions())) {
            return 40.0; // No restrictions
        }

        List<String> lowerRestrictions = preference.getDietaryRestrictions().stream()
                .map(String::toLowerCase)
                .toList();

        // Check recipe tags
        for (String tag : recipe.getTags()) {
            String lowerTag = tag.toLowerCase();
            for (String restriction : lowerRestrictions) {
                if (isViolatingRestriction(lowerTag, restriction)) {
                    return 0.0; // Violates dietary restriction
                }
            }
        }

        // Check ingredients
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            String lowerIngredient = ingredient.getName().toLowerCase();
            for (String restriction : lowerRestrictions) {
                if (isViolatingRestriction(lowerIngredient, restriction)) {
                    return 0.0; // Violates dietary restriction
                }
            }
        }

        return 40.0; // Passes all dietary restrictions
    }

    private boolean isViolatingRestriction(String item, String restriction) {
        // Common dietary restriction checks
        if (restriction.contains("vegetarian")) {
            return item.contains("meat") || item.contains("chicken") ||
                   item.contains("beef") || item.contains("pork") ||
                   item.contains("fish") || item.contains("seafood");
        }
        if (restriction.contains("vegan")) {
            return item.contains("meat") || item.contains("dairy") ||
                   item.contains("egg") || item.contains("honey") ||
                   item.contains("milk") || item.contains("cheese");
        }
        if (restriction.contains("gluten-free") || restriction.contains("gluten free")) {
            return item.contains("wheat") || item.contains("gluten") ||
                   item.contains("flour") || item.contains("bread");
        }
        if (restriction.contains("dairy-free") || restriction.contains("dairy free") ||
            restriction.contains("lactose")) {
            return item.contains("milk") || item.contains("cheese") ||
                   item.contains("butter") || item.contains("cream") ||
                   item.contains("yogurt");
        }
        if (restriction.contains("nut-free") || restriction.contains("nut free")) {
            return item.contains("nut") || item.contains("almond") ||
                   item.contains("peanut") || item.contains("cashew");
        }

        return false;
    }

    private double calculateDislikedIngredientsScore(Recipe recipe, UserPreference preference) {
        if (CollectionUtils.isEmpty(preference.getDislikedIngredients())) {
            return 20.0; // No dislikes
        }

        List<String> lowerDisliked = preference.getDislikedIngredients().stream()
                .map(String::toLowerCase)
                .toList();

        int violationCount = 0;
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            String lowerIngredient = ingredient.getName().toLowerCase();
            for (String disliked : lowerDisliked) {
                if (lowerIngredient.contains(disliked.toLowerCase())) {
                    violationCount++;
                }
            }
        }

        // Deduct 5 points per disliked ingredient, minimum 0
        return Math.max(0, 20.0 - (violationCount * 5.0));
    }

    private double calculateUserHistoryScore(Recipe recipe, User user) {
        if (CollectionUtils.isEmpty(user.getUserRecipes())) {
            return 5.0; // Neutral for new users
        }

        // Check if the user has saved similar cuisines
        String recipeCuisine = recipe.getCuisine().toLowerCase();
        long similarCuisineCount = user.getUserRecipes().stream()
                .filter(ur -> ur.getRecipe().getCuisine().toLowerCase().equals(recipeCuisine))
                .count();

        if (similarCuisineCount > 0) {
            return 10.0; // Bonus for similar cuisine
        }

        return 5.0; // Neutral
    }

    // Inner class for scoring
    private static class ScoredRecipe {
        private final Recipe recipe;
        private final double score;

        public ScoredRecipe(Recipe recipe, double score) {
            this.recipe = recipe;
            this.score = score;
        }

        public Recipe getRecipe() {
            return recipe;
        }

        public double getScore() {
            return score;
        }
    }
}
