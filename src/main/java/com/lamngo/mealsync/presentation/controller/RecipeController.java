package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.GenerateRecipeRequest;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.service.AI.GeminiService;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final GeminiService geminiService;

    public RecipeController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/gererate-recipes")
    public ResponseEntity<List<RecipeReadDto>> generateRecipesFromIngredients(
            @RequestBody @Valid GenerateRecipeRequest request) {
        List<String> ingredients = request.getIngredients();
        UserPreference userPreference = request.getUserPreference();

        if (ingredients == null || ingredients.isEmpty()) {
            throw new BadRequestException("Ingredient list cannot be empty");
        }
        List<RecipeReadDto> recipes = geminiService.generateRecipes(ingredients, userPreference);
        return ResponseEntity.ok(recipes);
    }
}
