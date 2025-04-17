package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.IRecipeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecipeRepo implements IRecipeRepo {

    @Autowired
    private RecipeJpaRepo _recipeJpaRepo;

    @Override
    public Recipe createRecipe(Recipe recipe) {
        return _recipeJpaRepo.save(recipe);
    }

    @Override
    public Optional<Recipe> getRecipeById(UUID id) {
        return _recipeJpaRepo.findById(id);
    }

    @Override
    public List<Recipe> getAllRecipes() {
        return _recipeJpaRepo.findAll();
    }


    @Override
    public void deleteRecipe(UUID id) {
        _recipeJpaRepo.deleteById(id);
    }
}
