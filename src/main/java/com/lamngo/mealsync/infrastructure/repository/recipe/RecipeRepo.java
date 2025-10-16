package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    
    @Override
    public Recipe saveRecipe(Recipe recipe) {
        return _recipeJpaRepo.save(recipe);
    }

    @Override
    public Page<Recipe> getAllRecipesFiltered(OffsetPage pageable, List<String> cuisines, List<String> tags,
                                               List<String> ingredients, String difficulty,
                                               Integer maxTotalTime, Integer minServings) {
        List<Recipe> allRecipes = _recipeJpaRepo.findAll();

        List<Recipe> filteredRecipes = allRecipes.stream()
                .filter(recipe -> {
                    // Filter by cuisines (if any cuisine matches)
                    boolean cuisineMatch = cuisines == null || cuisines.isEmpty() ||
                            cuisines.stream().anyMatch(c -> recipe.getCuisine().equalsIgnoreCase(c));

                    // Filter by tags (if any tag matches)
                    boolean tagMatch = tags == null || tags.isEmpty() ||
                            tags.stream().anyMatch(t -> recipe.getTags().stream().anyMatch(rt -> rt.equalsIgnoreCase(t)));

                    // Filter by ingredients (if any ingredient matches in recipe ingredients)
                    boolean ingredientMatch = ingredients == null || ingredients.isEmpty() ||
                            ingredients.stream().anyMatch(i -> recipe.getIngredients().stream()
                                    .anyMatch(ri -> ri.getName().toLowerCase().contains(i.toLowerCase())));

                    // Filter by difficulty
                    boolean difficultyMatch = difficulty == null || recipe.getDifficulty().equalsIgnoreCase(difficulty);

                    // Filter by maxTotalTime
                    boolean timeMatch = maxTotalTime == null || recipe.getTotalTime() <= maxTotalTime;

                    // Filter by minServings
                    boolean servingsMatch = minServings == null || recipe.getServings() >= minServings;

                    return cuisineMatch && tagMatch && ingredientMatch && difficultyMatch && timeMatch && servingsMatch;
                })
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredRecipes.size());
        List<Recipe> paginatedRecipes = filteredRecipes.subList(start, end);

        return new PageImpl<>(paginatedRecipes, pageable, filteredRecipes.size());
    }
}
