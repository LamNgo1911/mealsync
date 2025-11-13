package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeIngredientReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.mapper.UserRecipeMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeIngredientMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.UserRecipeType;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    private final S3Service s3Service;

    public RecipeService(IRecipeRepo recipeRepo, RecipeMapper recipeMapper,
                         IUserRecipeRepo userRecipeRepo, UserRecipeMapper userRecipeMapper, IUserRepo userRepo,
                         RecipeIngredientMapper recipeIngredientMapper, RecipeRecommendationService recommendationService,
                         S3Service s3Service) {
        this.recipeRepo = recipeRepo;
        this.recipeMapper = recipeMapper;
        this.userRecipeRepo = userRecipeRepo;
        this.userRecipeMapper = userRecipeMapper;
        this.userRepo = userRepo;
        this.recipeIngredientMapper = recipeIngredientMapper;
        this.recommendationService = recommendationService;
        this.s3Service = s3Service;
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
                    ingredient.setRecipe(recipe);
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
    public PaginationResponse<RecipeReadDto> getAllRecipes(int limit, int offset, String name, List<String> cuisines, List<String> tags,
                                                            List<String> ingredients, String difficulty,
                                                            Integer maxTotalTime, Integer minServings) {
        OffsetPage page = new OffsetPage(limit, offset);
        Page<Recipe> recipePage = recipeRepo.getAllRecipesFiltered(page, name, cuisines, tags, ingredients, difficulty, maxTotalTime, minServings);
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
        logger.info("Deleting recipe with ID: {}", id);

        // Fetch the recipe to get the image URL
        Recipe recipe = recipeRepo.getRecipeById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));

        // Delete the image from S3 if it exists
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            try {
                s3Service.deleteImage(recipe.getImageUrl());
                logger.info("Image deleted from S3 for recipe ID: {}", id);
            } catch (Exception e) {
                logger.error("Failed to delete image from S3 for recipe ID: {}, continuing with recipe deletion", id, e);
                // Continue with recipe deletion even if S3 deletion fails
            }
        }

        // Delete the recipe from the database
        recipeRepo.deleteRecipe(id);
        logger.info("Recipe deleted successfully with ID: {}", id);
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
        userRecipe.setType(UserRecipeType.SAVED);
        UserRecipe savedUserRecipe = userRecipeRepo.saveUserRecipe(userRecipe);
        return userRecipeMapper.toUserRecipeReadDto(savedUserRecipe);
    }

    @Override
    @Transactional
    public void removeRecipeFromUser(UUID userId, UUID recipeId) {
        logger.info("Removing recipe with ID {} from user with ID {}", recipeId, userId);
        userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        recipeRepo.getRecipeById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + recipeId));

        // Find the UserRecipe entry with SAVED type
        UserRecipe userRecipe = userRecipeRepo.getUserRecipeByUserIdAndRecipeIdAndType(userId, recipeId, UserRecipeType.SAVED)
                .orElseThrow(() -> new ResourceNotFoundException("Saved recipe not found for user"));

        userRecipeRepo.deleteUserRecipe(userRecipe.getId());
        logger.info("Successfully removed recipe with ID {} from user with ID {}", recipeId, userId);
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
    public PaginationResponse<UserRecipeReadDto> getSavedRecipesByUserId(UUID userId, int limit, int offset) {
        OffsetPage page = new OffsetPage(limit, offset, Sort.by(Sort.Direction.DESC, "savedAt"));
        Page<UserRecipe> userRecipePage = userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED, page);
        List<UserRecipeReadDto> userRecipeReadDtos = userRecipePage.getContent().stream()
                .map(userRecipeMapper::toUserRecipeReadDto)
                .collect(Collectors.toList());
        return PaginationResponse.<UserRecipeReadDto>builder()
                .data(userRecipeReadDtos)
                .offset(offset)
                .limit(limit)
                .totalElements(userRecipePage.getTotalElements())
                .hasNext(userRecipePage.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeReadDto> getTodayPicks(UUID userId) {
        logger.info("Getting today's picks for user with ID: {}", userId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Get more recommended recipes to have a good pool for random selection
        List<Recipe> recommendedRecipes = recommendationService.getRecommendedRecipes(user, 20);

        if (recommendedRecipes.isEmpty()) {
            logger.warn("No recommended recipes found for user with ID: {}", userId);
            return Collections.emptyList();
        }

        // Randomly select 2 recipes from the recommended list
        List<Recipe> todayPicks = new ArrayList<>();
        Random random = new Random();

        if (recommendedRecipes.size() <= 2) {
            todayPicks = recommendedRecipes;
        } else {
            // Create a copy to avoid modifying the original list
            List<Recipe> recipesPool = new ArrayList<>(recommendedRecipes);

            // Select first random recipe
            int firstIndex = random.nextInt(recipesPool.size());
            todayPicks.add(recipesPool.remove(firstIndex));

            // Select second random recipe
            int secondIndex = random.nextInt(recipesPool.size());
            todayPicks.add(recipesPool.remove(secondIndex));
        }

        logger.info("Successfully selected {} today's picks for user with ID: {}", todayPicks.size(), userId);

        return todayPicks.stream()
                .map(recipeMapper::toRecipeReadDto)
                .toList();
    }

    @Override
    @Transactional
    public void addGeneratedRecipesToUser(UUID userId, List<UUID> recipeIds) {
        logger.info("Adding {} generated recipes to user with ID: {}", recipeIds.size(), userId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        for (UUID recipeId : recipeIds) {
            Recipe recipe = recipeRepo.getRecipeById(recipeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + recipeId));

            UserRecipe userRecipe = new UserRecipe();
            userRecipe.setUser(user);
            userRecipe.setRecipe(recipe);
            userRecipe.setType(UserRecipeType.GENERATED);
            userRecipeRepo.saveUserRecipe(userRecipe);
        }
        logger.info("Successfully added {} generated recipes to user with ID: {}", recipeIds.size(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResponse<RecipeReadDto> getRecentGeneratedRecipes(UUID userId, int limit, int offset) {
        logger.info("Getting recent generated recipes for user with ID: {}", userId);
        OffsetPage page = new OffsetPage(limit, offset, Sort.by(Sort.Direction.DESC, "savedAt"));
        Page<UserRecipe> userRecipePage = userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.GENERATED, page);
        
        List<RecipeReadDto> recipeReadDtos = userRecipePage.getContent().stream()
                .map(ur -> recipeMapper.toRecipeReadDto(ur.getRecipe()))
                .collect(Collectors.toList());
        
        return PaginationResponse.<RecipeReadDto>builder()
                .data(recipeReadDtos)
                .offset(offset)
                .limit(limit)
                .totalElements(userRecipePage.getTotalElements())
                .hasNext(userRecipePage.hasNext())
                .build();
    }
}
