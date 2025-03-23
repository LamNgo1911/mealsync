package com.lamngo.mealsync.infrastructure.repository.mealPlan;

import com.lamngo.mealsync.domain.model.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MealPlanJpaRepo extends JpaRepository<MealPlan, String> {
    MealPlan save(MealPlan mealPlan);

    void deleteById(String id);

    void updateMealPlan(MealPlan mealPlan);

    Optional<MealPlan> findById(String id);

    List<MealPlan> findAll();

    List<MealPlan> findByUserId(String userId);

    List<MealPlan> findByRecipeId(String recipeId);

}
