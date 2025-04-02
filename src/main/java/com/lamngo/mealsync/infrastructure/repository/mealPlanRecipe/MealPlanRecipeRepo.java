package com.lamngo.mealsync.infrastructure.repository.mealPlanRecipe;

import com.lamngo.mealsync.domain.model.MealPlanRecipe;
import com.lamngo.mealsync.domain.repository.IMealPlanRecipeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MealPlanRecipeRepo implements IMealPlanRecipeRepo {

    @Autowired
    private MealPlanRecipeJpaRepo _mealPlanRecipeJpaRepo;


    @Override
    public void addRecipeToMealPlan(MealPlanRecipe mealPlanRecipe) {
        _mealPlanRecipeJpaRepo.save(mealPlanRecipe);
    }

    @Override
    public void removeRecipeFromMealPlan(MealPlanRecipe mealPlanRecipe) {
        _mealPlanRecipeJpaRepo.delete(mealPlanRecipe);
    }

    @Override
    public List<UUID> getRecipesByMealPlanId(UUID mealPlanId) {
        return _mealPlanRecipeJpaRepo.findByMealPlanId(mealPlanId)
                .stream()
                .map(mealPlanRecipe -> UUID.fromString(mealPlanRecipe.getRecipe().getId().toString()))
                .toList();
    }

    @Override
    public List<UUID> getMealPlansByRecipeId(UUID recipeId) {
        return _mealPlanRecipeJpaRepo.findByRecipeId(recipeId)
                .stream()
                .map(mealPlanRecipe -> UUID.fromString(mealPlanRecipe.getMealPlan().getId().toString()))
                .toList();
    }
}
