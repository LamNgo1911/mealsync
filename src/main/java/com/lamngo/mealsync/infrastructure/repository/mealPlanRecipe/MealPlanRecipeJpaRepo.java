package com.lamngo.mealsync.infrastructure.repository.mealPlanRecipe;

import com.lamngo.mealsync.domain.model.MealPlanRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MealPlanRecipeJpaRepo extends JpaRepository<MealPlanRecipe, UUID> {
    MealPlanRecipe save(MealPlanRecipe mealPlanRecipe);
    void deleteById(UUID id);
    Optional<MealPlanRecipe> findById(UUID id);
    List<MealPlanRecipe> findAll();
    List<MealPlanRecipe> findByMealPlanId(UUID mealPlanId);
    List<MealPlanRecipe> findByRecipeId(UUID recipeId);
}
