package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.mapper.UserRecipeMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.IUserRecipeRepo;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecipeService implements IRecipeService {
    private final IRecipeRepo recipeRepo;
    private final RecipeMapper recipeMapper;
    private final IUserRecipeRepo userRecipeRepo;
    private final UserRecipeMapper userRecipeMapper;
    private final IUserRepo userRepo; // Assuming you have a user repository

    @Autowired
    public RecipeService(IRecipeRepo recipeRepo, RecipeMapper recipeMapper,
                         IUserRecipeRepo userRecipeRepo, UserRecipeMapper userRecipeMapper, IUserRepo userRepo) {
        this.recipeRepo = recipeRepo;
        this.recipeMapper = recipeMapper;
        this.userRecipeRepo = userRecipeRepo;
        this.userRecipeMapper = userRecipeMapper;
        this.userRepo = userRepo;
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
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
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
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        recipeMapper.updateRecipeFromDto(recipeUpdateDto, recipe);
        Recipe updated = recipeRepo.createRecipe(recipe); // Assuming createRecipe acts as save/update
        return recipeMapper.toRecipeReadDto(updated);
    }

    @Override
    @Transactional
    public void deleteRecipe(UUID id) {
        recipeRepo.deleteRecipe(id);
    }

    @Override
    public void addRecipeToUser(UUID userId, UUID recipeId) {
        Recipe recipe = recipeRepo.getRecipeById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + recipeId));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UserRecipe userRecipe = new UserRecipe();
        userRecipe.setUser(user);
        userRecipe.setRecipe(recipe);

        userRecipeRepo.saveUserRecipe(userRecipe);
    }

}
