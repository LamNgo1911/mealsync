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

import jakarta.persistence.EntityManager;
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
    @Transactional(readOnly = true)
    public RecipeReadDto getRecipeById(UUID id) {
        
        Optional<Recipe> recipeOpt = recipeRepo.getRecipeById(id);
        return recipeOpt.map(recipeMapper::toRecipeReadDto)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeReadDto> getRecipesByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        
        // Fetch all recipes in a SINGLE batch query (not N individual queries)
        // This significantly improves performance when fetching multiple recipes
        return recipeRepo.getRecipesByIds(ids).stream()
                .map(recipeMapper::toRecipeReadDto)
                .toList();
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
        
        // Check if recipe is already saved for this user
        Optional<UserRecipe> existingUserRecipe = userRecipeRepo.getUserRecipeByUserIdAndRecipeIdAndType(
                userId, recipeId, UserRecipeType.SAVED);
        
        if (existingUserRecipe.isPresent()) {
            logger.info("Recipe with ID {} is already saved for user with ID {}, returning existing entry", recipeId, userId);
            return userRecipeMapper.toUserRecipeReadDto(existingUserRecipe.get());
        }
        
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
        // Fetch all saved recipes for the user
        List<UserRecipe> allUserRecipes = userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED);
        
        logger.debug("Fetched {} saved recipes for user {}", allUserRecipes.size(), userId);
        
        // Filter out any entries with null recipe (safety check)
        List<UserRecipe> validUserRecipes = allUserRecipes.stream()
                .filter(ur -> ur != null && ur.getRecipe() != null && ur.getRecipe().getId() != null)
                .collect(Collectors.toList());
        
        if (validUserRecipes.size() != allUserRecipes.size()) {
            logger.warn("Filtered out {} invalid UserRecipe entries (null recipe or recipe ID) for user {}", 
                       allUserRecipes.size() - validUserRecipes.size(), userId);
        }
        
        // Sort by savedAt DESC to ensure most recent entries come first
        validUserRecipes.sort((a, b) -> {
            if (a.getSavedAt() == null && b.getSavedAt() == null) return 0;
            if (a.getSavedAt() == null) return 1;
            if (b.getSavedAt() == null) return -1;
            return b.getSavedAt().compareTo(a.getSavedAt()); // DESC order
        });
        
        // Extract recipe IDs and fetch recipes directly from database to get latest imageUrl
        // This ensures we get the most up-to-date Recipe entities, including async-updated image URLs
        // The lazy-loaded Recipe from UserRecipe might be stale from Hibernate session cache
        List<UUID> recipeIds = validUserRecipes.stream()
                .map(ur -> ur.getRecipe().getId())
                .distinct()
                .collect(Collectors.toList());
        
        // Fetch recipes directly by IDs to get latest data (including updated imageUrl)
        Map<UUID, RecipeReadDto> recipeMap = getRecipesByIds(recipeIds).stream()
                .collect(Collectors.toMap(RecipeReadDto::getId, dto -> dto));
        
        // Map to DTOs using fresh recipes
        List<UserRecipeReadDto> allDtos = validUserRecipes.stream()
                .map(ur -> {
                    // Map UserRecipe to DTO first
                    UserRecipeReadDto dto = userRecipeMapper.toUserRecipeReadDto(ur);
                    // Replace with fresh RecipeReadDto that has latest imageUrl
                    if (dto != null && dto.getRecipe() != null) {
                        RecipeReadDto freshRecipe = recipeMap.get(dto.getRecipe().getId());
                        if (freshRecipe != null) {
                            dto.setRecipe(freshRecipe);
                        }
                    }
                    return dto;
                })
                .filter(dto -> dto != null && dto.getRecipe() != null && dto.getRecipe().getId() != null)
                .collect(Collectors.toList());
        
        // Apply pagination
        int totalElements = allDtos.size();
        int start = Math.min(offset, totalElements);
        int end = Math.min(offset + limit, totalElements);
        List<UserRecipeReadDto> paginatedRecipes = start < end 
                ? allDtos.subList(start, end)
                : List.of();
        
        logger.debug("Returning {} saved recipes (page: offset={}, limit={}, total={}) for user {}", 
                    paginatedRecipes.size(), offset, limit, totalElements, userId);
        
        return PaginationResponse.<UserRecipeReadDto>builder()
                .data(paginatedRecipes)
                .offset(offset)
                .limit(limit)
                .totalElements((long) totalElements)
                .hasNext(end < totalElements)
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

        // Deduplicate recipeIds in the same batch (in case AI generates duplicates)
        List<UUID> uniqueRecipeIds = recipeIds.stream()
                .distinct()
                .collect(Collectors.toList());
        
        if (uniqueRecipeIds.size() != recipeIds.size()) {
            logger.debug("Deduplicated {} duplicate recipe IDs in batch for user {}", 
                        recipeIds.size() - uniqueRecipeIds.size(), userId);
        }

        int addedCount = 0;
        int skippedCount = 0;
        
        for (UUID recipeId : uniqueRecipeIds) {
            Recipe recipe = recipeRepo.getRecipeById(recipeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + recipeId));

            // Check if this recipe is already generated for this user
            Optional<UserRecipe> existingUserRecipe = userRecipeRepo.getUserRecipeByUserIdAndRecipeIdAndType(
                    userId, recipeId, UserRecipeType.GENERATED);
            
            if (existingUserRecipe.isPresent()) {
                logger.debug("Recipe with ID {} already exists as generated for user {}, skipping", recipeId, userId);
                skippedCount++;
                continue;
            }

            UserRecipe userRecipe = new UserRecipe();
            userRecipe.setUser(user);
            userRecipe.setRecipe(recipe);
            userRecipe.setType(UserRecipeType.GENERATED);
            userRecipeRepo.saveUserRecipe(userRecipe);
            addedCount++;
        }
        
        logger.info("Successfully added {} generated recipes to user {} (skipped {} duplicates)", 
                   addedCount, userId, skippedCount);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResponse<RecipeReadDto> getRecentGeneratedRecipes(UUID userId, int limit, int offset) {
        logger.info("Getting recent generated recipes for user with ID: {}", userId);
        
        // Fetch all generated recipes for the user
        List<UserRecipe> allUserRecipes = userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.GENERATED);
        
        logger.debug("Fetched {} generated recipes for user {}", allUserRecipes.size(), userId);
        
        // Filter out any entries with null recipe (safety check)
        List<UserRecipe> validUserRecipes = allUserRecipes.stream()
                .filter(ur -> ur != null && ur.getRecipe() != null && ur.getRecipe().getId() != null)
                .collect(Collectors.toList());
        
        if (validUserRecipes.size() != allUserRecipes.size()) {
            logger.warn("Filtered out {} invalid UserRecipe entries (null recipe or recipe ID) for user {}", 
                       allUserRecipes.size() - validUserRecipes.size(), userId);
        }
        
        // Sort by savedAt DESC to ensure most recent entries come first
        validUserRecipes.sort((a, b) -> {
            if (a.getSavedAt() == null && b.getSavedAt() == null) return 0;
            if (a.getSavedAt() == null) return 1;
            if (b.getSavedAt() == null) return -1;
            return b.getSavedAt().compareTo(a.getSavedAt()); // DESC order
        });
        
        // Extract recipe IDs and fetch recipes directly from database to get latest imageUrl
        // This ensures we get the most up-to-date Recipe entities, including async-updated image URLs
        // The lazy-loaded Recipe from UserRecipe might be stale from Hibernate session cache
        List<UUID> recipeIds = validUserRecipes.stream()
                .map(ur -> ur.getRecipe().getId())
                .distinct()
                .collect(Collectors.toList());
        
        // Fetch recipes directly by IDs to get latest data (including updated imageUrl)
        Map<UUID, RecipeReadDto> recipeMap = getRecipesByIds(recipeIds).stream()
                .collect(Collectors.toMap(RecipeReadDto::getId, dto -> dto));
        
        // Map to RecipeReadDto using the freshly fetched recipes, preserving order
        List<RecipeReadDto> allRecipeDtos = validUserRecipes.stream()
                .map(ur -> recipeMap.get(ur.getRecipe().getId()))
                .filter(dto -> dto != null && dto.getId() != null)
                .collect(Collectors.toList());
        
        // Deduplicate by recipe ID, keeping the most recent entry (first in sorted list)
        // Use LinkedHashMap to preserve insertion order (most recent first)
        Map<UUID, RecipeReadDto> uniqueRecipesMap = new LinkedHashMap<>();
        for (RecipeReadDto dto : allRecipeDtos) {
            UUID recipeId = dto.getId();
            // Only add if we haven't seen this recipe ID before (keep first/most recent)
            uniqueRecipesMap.putIfAbsent(recipeId, dto);
        }
        
        List<RecipeReadDto> deduplicatedRecipes = new ArrayList<>(uniqueRecipesMap.values());
        
        int duplicatesRemoved = allRecipeDtos.size() - deduplicatedRecipes.size();
        if (duplicatesRemoved > 0) {
            logger.info("Removed {} duplicate generated recipe entries for user {} (kept most recent)", duplicatesRemoved, userId);
        }
        
        // Apply pagination to deduplicated list
        int totalElements = deduplicatedRecipes.size();
        int start = Math.min(offset, totalElements);
        int end = Math.min(offset + limit, totalElements);
        List<RecipeReadDto> paginatedRecipes = start < end 
                ? deduplicatedRecipes.subList(start, end)
                : List.of();
        
        logger.debug("Returning {} generated recipes (page: offset={}, limit={}, total={}) for user {}", 
                    paginatedRecipes.size(), offset, limit, totalElements, userId);
        
        return PaginationResponse.<RecipeReadDto>builder()
                .data(paginatedRecipes)
                .offset(offset)
                .limit(limit)
                .totalElements((long) totalElements)
                .hasNext(end < totalElements)
                .build();
    }
}
