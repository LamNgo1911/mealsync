package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.MealPlanRecipe;

import java.util.List;

public interface IMealPlanRecipeRepo {
    void addRecipeToMealPlan(MealPlanRecipe mealPlanRecipe);
    void removeRecipeFromMealPlan(MealPlanRecipe mealPlanRecipe);
    List<String> getRecipesByMealPlanId(String mealPlanId);
    List<String> getMealPlansByRecipeId(String recipeId);
}
