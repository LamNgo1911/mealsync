package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.MealPlan;

import java.util.List;

public interface IMealPlanRepo {
    MealPlan createMealPlan(MealPlan mealPlan);
    MealPlan getMealPlanById(String id);
    List<MealPlan> getAllMealPlans();
    void updateMealPlan(MealPlan mealPlan);
    void deleteMealPlan(String id);
}
