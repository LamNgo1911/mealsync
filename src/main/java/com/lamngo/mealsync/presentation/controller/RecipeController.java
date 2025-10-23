package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.GenerateRecipeRequest;
import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeCreateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.IngredientDetectionService;
import com.lamngo.mealsync.application.service.recipe.RecipeService;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {
    private Logger logger = LoggerFactory.getLogger(RecipeController.class);
    private final AIRecipeService aiRecipeService;
    private final RecipeService recipeService;
    private final IngredientDetectionService ingredientDetectionService;

    public RecipeController(RecipeService recipeService, AIRecipeService aiRecipeService, IngredientDetectionService ingredientDetectionService) {
        this.aiRecipeService = aiRecipeService;
        this.recipeService = recipeService;
        this.ingredientDetectionService = ingredientDetectionService;
    }

    @PostMapping(value = "/generate-recipes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> generateRecipes(
           @RequestBody @Valid GenerateRecipeRequest request) {

        List<String> ingredients = request.getIngredients();
        UserPreference userPreference = request.getUserPreference();

        if (ingredients == null || ingredients.isEmpty()) {
            throw new BadRequestException("Ingredients list is empty or null");
        }

        if (userPreference == null) {
            userPreference = new UserPreference();
        }

        logger.info("Generating recipes from {} ingredients", ingredients.size());
        logger.info("User preference: {}", userPreference);

        // Then, generate recipes from the detected ingredients
        List<RecipeReadDto> recipes = aiRecipeService.generateRecipes(ingredients, userPreference);
        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recipes);
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/detect-ingredients", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<String>>> detectIngredients(
            @RequestPart("image") MultipartFile image) {

        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Image file cannot be empty");
        }

        logger.info("Detecting raw ingredients from uploaded image: {}", image.getOriginalFilename());
        List<String> ingredients = ingredientDetectionService.detectRawIngredients(image);
        SuccessResponseEntity<List<String>> body = new SuccessResponseEntity<>();
        body.setData(ingredients);
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
