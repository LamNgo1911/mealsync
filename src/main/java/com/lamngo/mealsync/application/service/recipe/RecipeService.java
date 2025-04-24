package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.repository.IRecipeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecipeService implements IRecipeService {
    private final IRecipeRepo recipeRepo;
    private final RecipeMapper recipeMapper;

    @Autowired
    public RecipeService(IRecipeRepo recipeRepo, RecipeMapper recipeMapper) {
        this.recipeRepo = recipeRepo;
        this.recipeMapper = recipeMapper;
    }

    @Override
    @Transactional
    public RecipeReadDto createRecipe(RecipeCreateDto recipeCreateDto) {
        Recipe recipe = recipeMapper.toRecipe(recipeCreateDto);
        Recipe saved = recipeRepo.createRecipe(recipe);
        return recipeMapper.toRecipeReadDto(saved);
    }

    @Override
    public RecipeReadDto getRecipeById(UUID id) {
        Optional<Recipe> recipeOpt = recipeRepo.getRecipeById(id);
        return recipeOpt.map(recipeMapper::toRecipeReadDto)
                .orElseThrow(() -> new RuntimeException("Recipe not found with id: " + id));
    }

    @Override
    public List<RecipeReadDto> getAllRecipes() {
        List<Recipe> recipes = recipeRepo.getAllRecipes();
        return recipeMapper.toRecipeReadDtoList(recipes);
    }

    @Override
    @Transactional
    public RecipeReadDto updateRecipe(UUID id, RecipeUpdateDto recipeUpdateDto) {
        Recipe recipe = recipeRepo.getRecipeById(id)
                .orElseThrow(() -> new RuntimeException("Recipe not found with id: " + id));
        recipeMapper.updateRecipeFromDto(recipeUpdateDto, recipe);
        Recipe updated = recipeRepo.createRecipe(recipe); // Assuming createRecipe acts as save/update
        return recipeMapper.toRecipeReadDto(updated);
    }

    @Override
    @Transactional
    public void deleteRecipe(UUID id) {
        recipeRepo.deleteRecipe(id);
    }
}
