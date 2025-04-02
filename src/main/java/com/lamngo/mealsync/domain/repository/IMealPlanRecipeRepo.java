package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.MealPlanRecipe;

import java.util.List;
import java.util.UUID;

public interface IMealPlanRecipeRepo {
    void addRecipeToMealPlan(MealPlanRecipe mealPlanRecipe);
    void removeRecipeFromMealPlan(MealPlanRecipe mealPlanRecipe);
    List<UUID> getRecipesByMealPlanId(UUID mealPlanId);
    List<UUID> getMealPlansByRecipeId(UUID recipeId);
}
