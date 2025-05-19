package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.GenerateRecipeRequest;
import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeCreateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.service.AI.GeminiService;
import com.lamngo.mealsync.application.service.recipe.RecipeService;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<SuccessResponseEntity<RecipeReadDto>> createRecipe(@RequestBody @Valid RecipeCreateDto recipeCreateDto) {
        RecipeReadDto created = recipeService.createRecipe(recipeCreateDto);
        SuccessResponseEntity<RecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(created);
        return new ResponseEntity<>(body, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponseEntity<RecipeReadDto>> getRecipeById(@PathVariable("id") String id) {
        RecipeReadDto recipe = recipeService.getRecipeById(UUID.fromString(id));
        SuccessResponseEntity<RecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(recipe);
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> getAllRecipes() {
        List<RecipeReadDto> recipes = recipeService.getAllRecipes();
        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recipes);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponseEntity<RecipeReadDto>> updateRecipe(@PathVariable("id") String id, @RequestBody @Valid RecipeUpdateDto updateDto) {
        RecipeReadDto updated = recipeService.updateRecipe(UUID.fromString(id), updateDto);
        SuccessResponseEntity<RecipeReadDto> body = new SuccessResponseEntity<>();
        body.setData(updated);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable("id") String id) {
        recipeService.deleteRecipe(UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
