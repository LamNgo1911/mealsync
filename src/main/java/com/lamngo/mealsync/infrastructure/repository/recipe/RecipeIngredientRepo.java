package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeIngredient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RecipeIngredientRepo implements IRecipeIngredient {
    private final RecipeIngredientJpaRepo recipeIngredientJpaRepo;

    public RecipeIngredientRepo(RecipeIngredientJpaRepo recipeIngredientJpaRepo) {
        this.recipeIngredientJpaRepo = recipeIngredientJpaRepo;
    }

    @Override
    public RecipeIngredient createIngredient(RecipeIngredient recipeIngredient) {
        return recipeIngredientJpaRepo.save(recipeIngredient);
    }

    @Override
    public void deleteIngredientById(UUID id) {
        recipeIngredientJpaRepo.deleteById(id);
    }

    @Override
    public RecipeIngredient getIngredientById(UUID id) {
        return recipeIngredientJpaRepo.findById(id).orElse(null);
    }

    @Override
    public List<RecipeIngredient> getAllIngredients() {
        return recipeIngredientJpaRepo.findAll();
    }

    @Override
    public List<RecipeIngredient> getIngredientsByRecipeId(UUID recipeId) {
        return recipeIngredientJpaRepo.findAllByRecipeId(recipeId);
    }
}
