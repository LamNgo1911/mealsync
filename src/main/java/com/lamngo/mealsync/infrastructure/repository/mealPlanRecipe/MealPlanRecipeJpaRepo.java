package com.lamngo.mealsync.infrastructure.repository.mealPlanRecipe;

import com.lamngo.mealsync.domain.model.MealPlanRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MealPlanRecipeJpaRepo extends JpaRepository<MealPlanRecipe, String> {
    MealPlanRecipe save(MealPlanRecipe mealPlanRecipe);
    void deleteById(String id);
    Optional<MealPlanRecipe> findById(String id);
    List<MealPlanRecipe> findAll();
    List<MealPlanRecipe> findByMealPlanId(String mealPlanId);
    List<MealPlanRecipe> findByRecipeId(String recipeId);
}
