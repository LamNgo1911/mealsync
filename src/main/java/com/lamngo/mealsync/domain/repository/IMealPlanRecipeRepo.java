package com.lamngo.mealsync.domain.repository;

import java.util.List;

public interface IMealPlanRecipeRepo {
    void addRecipeToMealPlan(String mealPlanId, String recipeId);
    void removeRecipeFromMealPlan(String mealPlanId, String recipeId);
    List<String> getRecipesByMealPlanId(String mealPlanId);
    List<String> getMealPlansByRecipeId(String recipeId);
}
