package com.lamngo.mealsync.infrastructure.repository.mealPlanRecipe;

import com.lamngo.mealsync.domain.model.MealPlanRecipe;
import com.lamngo.mealsync.domain.repository.IMealPlanRecipeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    public List<String> getRecipesByMealPlanId(String mealPlanId) {
        return _mealPlanRecipeJpaRepo.findByMealPlanId(mealPlanId)
                .stream()
                .map(mealPlanRecipe -> mealPlanRecipe.getRecipe().getId().toString())
                .toList();
    }

    @Override
    public List<String> getMealPlansByRecipeId(String recipeId) {
        return _mealPlanRecipeJpaRepo.findByRecipeId(recipeId)
                .stream()
                .map(mealPlanRecipe -> mealPlanRecipe.getMealPlan().getId().toString())
                .toList();
    }
}
