package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.MealPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IMealPlanRepo {
    MealPlan createMealPlan(MealPlan mealPlan);
    Optional<MealPlan> getMealPlanById(UUID id);
    List<MealPlan> getAllMealPlans();
    void deleteMealPlan(UUID id);
}
