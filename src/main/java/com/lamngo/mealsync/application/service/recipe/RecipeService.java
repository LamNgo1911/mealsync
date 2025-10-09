package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeIngredientReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.mapper.UserRecipeMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeIngredientMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.IUserRecipeRepo;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecipeService implements IRecipeService {
    private static final Logger logger = LoggerFactory.getLogger(RecipeService.class);
    private final IRecipeRepo recipeRepo;
    private final RecipeMapper recipeMapper;
    private final IUserRecipeRepo userRecipeRepo;
    private final UserRecipeMapper userRecipeMapper;
    private final IUserRepo userRepo;
    private final RecipeIngredientMapper recipeIngredientMapper;
    private final RecipeRecommendationService recommendationService;

    public RecipeService(IRecipeRepo recipeRepo, RecipeMapper recipeMapper,
                         IUserRecipeRepo userRecipeRepo, UserRecipeMapper userRecipeMapper, IUserRepo userRepo,
                         RecipeIngredientMapper recipeIngredientMapper, RecipeRecommendationService recommendationService) {
        this.recipeRepo = recipeRepo;
        this.recipeMapper = recipeMapper;
        this.userRecipeRepo = userRecipeRepo;
        this.userRecipeMapper = userRecipeMapper;
        this.userRepo = userRepo;
        this.recipeIngredientMapper = recipeIngredientMapper;
        this.recommendationService = recommendationService;
    }

    @Override
    @Transactional
    public RecipeReadDto createRecipe(RecipeCreateDto recipeCreateDto) {
        Recipe recipe = recipeMapper.toRecipe(recipeCreateDto);
        List<RecipeIngredient> ingredients = recipeCreateDto.getIngredients().stream()
                .map(dto -> {
                    RecipeIngredient ingredient = new RecipeIngredient();
                    ingredient.setName(dto.getName());
                    ingredient.setQuantity(dto.getQuantity());
                    ingredient.setUnit(dto.getUnit());
                    ingredient.setRecipe(recipe); // associate with recipe
                    return ingredient;
                })
                .toList();
        recipe.setIngredients(ingredients);
        Recipe savedRecipe = recipeRepo.createRecipe(recipe);
        return recipeMapper.toRecipeReadDto(savedRecipe);
    }

    @Override
    public RecipeReadDto getRecipeById(UUID id) {
        Optional<Recipe> recipeOpt = recipeRepo.getRecipeById(id);
        return recipeOpt.map(recipeMapper::toRecipeReadDto)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
    }

    @Override
    public PaginationResponse<RecipeReadDto> getAllRecipes(int limit, int offset) {
        OffsetPage page = new OffsetPage(limit, offset);
        Page<Recipe> recipePage = recipeRepo.getAllRecipes(page);
        List<RecipeReadDto> recipeReadDtos = recipePage.getContent().stream()
                .map(recipeMapper::toRecipeReadDto)
                .collect(Collectors.toList());
        return PaginationResponse.<RecipeReadDto>builder()
                .data(recipeReadDtos)
                .offset(offset)
                .limit(limit)
                .totalElements(recipePage.getTotalElements())
                .hasNext(recipePage.hasNext())
                .build();
    }

    @Override
    @Transactional
    public RecipeReadDto updateRecipe(UUID id, RecipeUpdateDto recipeUpdateDto) {
        Recipe recipe = recipeRepo.getRecipeById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
        recipeMapper.updateRecipeFromDto(recipeUpdateDto, recipe);

        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                ingredient.setRecipe(recipe);
            }
        }

        Recipe updated = recipeRepo.createRecipe(recipe);
        return recipeMapper.toRecipeReadDto(updated);
    }

    @Override
    @Transactional
    public void deleteRecipe(UUID id) {
        recipeRepo.deleteRecipe(id);
    }

    @Override
    public UserRecipeReadDto addRecipeToUser(UUID userId, UUID recipeId) {
        logger.info("Adding recipe with ID {} to user with ID {}", recipeId, userId);
        Recipe recipe = recipeRepo.getRecipeById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + recipeId));
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        UserRecipe userRecipe = new UserRecipe();
        userRecipe.setUser(user);
        userRecipe.setRecipe(recipe);
        UserRecipe savedUserRecipe = userRecipeRepo.saveUserRecipe(userRecipe);
        return userRecipeMapper.toUserRecipeReadDto(savedUserRecipe);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeReadDto> getRecommendedRecipes(UUID userId, int limit) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<Recipe> recommendedRecipes = recommendationService.getRecommendedRecipes(user, limit);

        return recommendedRecipes.stream()
                .map(recipeMapper::toRecipeReadDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRecipeReadDto> getSavedRecipesByUserId(UUID userId) {
        List<UserRecipe> userRecipes = userRecipeRepo.getUserRecipesByUserId(userId);

        return userRecipes.stream()
                .map(userRecipeMapper::toUserRecipeReadDto)
                .toList();
    }
}
