package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    public Page<Recipe> getAllRecipes(OffsetPage pageable) {
        return _recipeJpaRepo.findAll(pageable);
    }

    @Override
    public void deleteRecipe(UUID id) {
        _recipeJpaRepo.deleteById(id);
    }

    @Override
    public Optional<Recipe> findByIngredientKey(String ingredientKey) {
        return _recipeJpaRepo.findByIngredientKey(ingredientKey);
    }

    @Override
    public long countAllRecipes() {
        return _recipeJpaRepo.count();
    }

    @Override
    public List<Recipe> findRecipesByCuisines(List<String> cuisines, int limit) {
        List<String> lowerCaseCuisines = cuisines.stream()
                .map(String::toLowerCase)
                .toList();
        return _recipeJpaRepo.findByCuisineInIgnoreCase(lowerCaseCuisines, PageRequest.of(0, limit));
    }

    @Override
    public List<Recipe> findAllRecipesForRecommendation(int limit) {
        return _recipeJpaRepo.findAll(PageRequest.of(0, limit)).getContent();
    }
}
