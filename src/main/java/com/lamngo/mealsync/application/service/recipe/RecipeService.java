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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecipeService implements IRecipeService {
    private static final Logger logger = LoggerFactory.getLogger(RecipeService.class);
    private final IRecipeRepo recipeRepo;
    private final RecipeMapper recipeMapper;
    private final IUserRecipeRepo userRecipeRepo;
    private final UserRecipeMapper userRecipeMapper;
    private final IUserRepo userRepo;
    private final RecipeIngredientMapper recipeIngredientMapper;

    public RecipeService(IRecipeRepo recipeRepo, RecipeMapper recipeMapper,
                         IUserRecipeRepo userRecipeRepo, UserRecipeMapper userRecipeMapper, IUserRepo userRepo,
                         RecipeIngredientMapper recipeIngredientMapper) {
        this.recipeRepo = recipeRepo;
        this.recipeMapper = recipeMapper;
        this.userRecipeRepo = userRecipeRepo;
        this.userRecipeMapper = userRecipeMapper;
        this.userRepo = userRepo;
        this.recipeIngredientMapper = recipeIngredientMapper;
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
    public PaginationResponse<RecipeReadDto> getAllRecipes(OffsetPage page) {
        int offset = (int) Math.max(0, page.getOffset());
        int limit = Math.max(1, page.getPageSize());

        List<Recipe> recipes = recipeRepo.getAllRecipes(offset, limit);
        long total = recipeRepo.countAllRecipes();
        List<RecipeReadDto> recipeDtos = recipeMapper.toRecipeReadDtoList(recipes);
        boolean hasNext = (offset + limit) < total;

        return PaginationResponse.<RecipeReadDto>builder()
                .data(recipeDtos)
                .offset(offset)
                .limit(limit)
                .totalElements(total)
                .hasNext(hasNext)
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
}
