package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.GenerateRecipeRequest;
import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeCreateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.service.AI.GeminiService;
import com.lamngo.mealsync.application.service.recipe.RecipeService;
import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {
    private Logger logger = LoggerFactory.getLogger(RecipeController.class);
    private final GeminiService geminiService;
    private final RecipeService recipeService;

    public RecipeController(GeminiService geminiService, RecipeService recipeService) {
        this.geminiService = geminiService;
        this.recipeService = recipeService;
    }

    @PostMapping("/generate-recipes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> generateRecipesFromIngredients(
            @RequestBody @Valid GenerateRecipeRequest request) {
        List<String> ingredients = request.getIngredients();
        UserPreference userPreference = request.getUserPreference();

        if (ingredients == null || ingredients.isEmpty()) {
            throw new BadRequestException("Ingredient list cannot be empty");
        }
        List<RecipeReadDto> recipes = geminiService.generateRecipes(ingredients, userPreference);
        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recipes);
        return ResponseEntity.ok(body);
    }

    // Save recipe with user
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN') or #userRecipeCreateDto.userId == authentication.principal.id")
    public ResponseEntity<SuccessResponseEntity<UserRecipeReadDto>> saveRecipeWithUser(@RequestBody @Valid
            UserRecipeCreateDto userRecipeCreateDto) {
        logger.info("Saving recipe with user: {}", userRecipeCreateDto);
        UserRecipeReadDto userRecipeReadDto = recipeService.addRecipeToUser(userRecipeCreateDto.getUserId(), userRecipeCreateDto.getRecipeId());
        SuccessResponseEntity<UserRecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(userRecipeReadDto);
        return ResponseEntity.ok(body);
    }

    // CRUD endpoints for RecipeService
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<RecipeReadDto>> createRecipe(@RequestBody @Valid RecipeCreateDto recipeCreateDto) {
        RecipeReadDto created = recipeService.createRecipe(recipeCreateDto);
        SuccessResponseEntity<RecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(created);
        return new ResponseEntity<>(body, HttpStatus.CREATED);
    }

    @GetMapping("/recommended")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> getRecommendedRecipes(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        UUID userId = user.getId();
        List<RecipeReadDto> recommendedRecipes = recipeService.getRecommendedRecipes(userId, limit);

        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recommendedRecipes);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/saved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<UserRecipeReadDto>>> getSavedRecipes(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {

        UUID userId = user.getId();
        List<UserRecipeReadDto> savedRecipes = recipeService.getSavedRecipesByUserId(userId, limit);

        SuccessResponseEntity<List<UserRecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(savedRecipes);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<RecipeReadDto>> getRecipeById(@PathVariable("id") String id) {
        RecipeReadDto recipe = recipeService.getRecipeById(UUID.fromString(id));
        SuccessResponseEntity<RecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(recipe);
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public ResponseEntity<PaginationResponse<RecipeReadDto>> getAllRecipes(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "2") int limit,
            @RequestParam(required = false) List<String> cuisines,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) List<String> ingredients,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Integer maxTotalTime,
            @RequestParam(required = false) Integer minServings) {

        PaginationResponse<RecipeReadDto> response;

        // Check if any filter is applied
        boolean hasFilters = (cuisines != null && !cuisines.isEmpty()) ||
                           (tags != null && !tags.isEmpty()) ||
                           (ingredients != null && !ingredients.isEmpty()) ||
                           difficulty != null ||
                           maxTotalTime != null ||
                           minServings != null;

        if (hasFilters) {
            response = recipeService.getAllRecipes(limit, offset, cuisines, tags, ingredients,
                                                   difficulty, maxTotalTime, minServings);
        } else {
            response = recipeService.getAllRecipes(limit, offset);
        }

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponseEntity<RecipeReadDto>> updateRecipe(@PathVariable("id") String id, @RequestBody @Valid RecipeUpdateDto updateDto) {
        RecipeReadDto updated = recipeService.updateRecipe(UUID.fromString(id), updateDto);
        SuccessResponseEntity<RecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(updated);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRecipe(@PathVariable("id") String id) {
        recipeService.deleteRecipe(UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
