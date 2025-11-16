package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.GenerateRecipeRequest;
import com.lamngo.mealsync.application.dto.recipe.ManualIngredientDetectionRequest;
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
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

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
           @RequestBody @Valid GenerateRecipeRequest request,
           @AuthenticationPrincipal User user) {

        List<DetectedIngredientDto> ingredients = request.getIngredients();

        if (ingredients == null || ingredients.isEmpty()) {
            throw new BadRequestException("Ingredients list is empty or null");
        }

        // Convert DTO to entity
        UserPreference userPreference = new UserPreference();
        if (request.getUserPreference() != null) {
            userPreference.setDietaryRestrictions(request.getUserPreference().getDietaryRestrictions());
            userPreference.setFavoriteCuisines(request.getUserPreference().getFavoriteCuisines());
            userPreference.setDislikedIngredients(request.getUserPreference().getDislikedIngredients());
        } else {
            // Set empty lists as defaults
            userPreference.setDietaryRestrictions(List.of());
            userPreference.setFavoriteCuisines(List.of());
            userPreference.setDislikedIngredients(List.of());
        }

        logger.info("Generating recipes from {} ingredients for user {}", ingredients.size(), user.getId());
        logger.info("User preference: {}", userPreference);

        // Generate recipes from the detected ingredients
        List<RecipeReadDto> recipes = aiRecipeService.generateRecipes(ingredients, userPreference);

        // Save generated recipes to user's history asynchronously (non-blocking)
        List<UUID> recipeIds = recipes.stream()
                .map(RecipeReadDto::getId)
                .toList();
        aiRecipeService.saveGeneratedRecipesToUserAsync(user.getId(), recipeIds);

        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recipes);
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/detect-ingredients", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> detectIngredients(
            @RequestPart("image") MultipartFile image) {

        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Image file cannot be empty");
        }

        logger.info("Detecting raw ingredients from uploaded image: {}", image.getOriginalFilename());
        List<DetectedIngredientDto> ingredients = ingredientDetectionService.detectRawIngredients(image);
        SuccessResponseEntity<List<DetectedIngredientDto>> body = new SuccessResponseEntity<>();
        body.setData(ingredients);
        return ResponseEntity.ok(body);
    }

    /**
     * Combined endpoint that scans an image for ingredients and generates recipes in a single request.
     * This endpoint pipelines operations for better performance - recipe generation starts as soon as
     * ingredients are detected, and images are generated asynchronously in the background.
     * 
     * @param image The uploaded image file containing ingredients
     * @param userPreferenceJson Optional JSON string containing user preferences (dietary restrictions, favorite cuisines, disliked ingredients)
     * @param user The authenticated user
     * @return List of generated recipes (images will be generated asynchronously in background)
     */
    @PostMapping(value = "/scan-and-generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> scanAndGenerateRecipes(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "userPreference", required = false) String userPreferenceJson,
            @AuthenticationPrincipal User user) {

        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Image file cannot be empty");
        }

        logger.info("Scanning image and generating recipes for user {}", user.getId());

        // Parse user preference
        UserPreference userPreference = parseUserPreference(userPreferenceJson);

        // PIPELINE: Start recipe generation as soon as ingredients are detected
        CompletableFuture<List<DetectedIngredientDto>> ingredientsFuture = 
            ingredientDetectionService.detectRawIngredientsAsync(image);

        // Start recipe generation immediately when ingredients are ready (pipeline)
        CompletableFuture<List<RecipeReadDto>> recipesFuture = ingredientsFuture
            .thenCompose(ingredients -> {
                logger.info("Ingredients detected, starting recipe generation");
                return aiRecipeService.fetchRecipesFromOpenAIAsync(ingredients, userPreference);
            });

        // Block only to get recipes (images generated async in background)
        List<RecipeReadDto> recipes = recipesFuture.join();

        // Generate images completely asynchronously (fire and forget - saves ~10s)
        aiRecipeService.addImagesToRecipesBatchAsync(recipes);

        // Save history asynchronously
        List<UUID> recipeIds = recipes.stream().map(RecipeReadDto::getId).toList();
        aiRecipeService.saveGeneratedRecipesToUserAsync(user.getId(), recipeIds);

        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recipes);
        return ResponseEntity.ok(body);
    }

    /**
     * Parses user preference from JSON string.
     * Returns default UserPreference if parsing fails or JSON is null/empty.
     */
    private UserPreference parseUserPreference(String userPreferenceJson) {
        UserPreference userPreference = new UserPreference();
        if (userPreferenceJson != null && !userPreferenceJson.isEmpty()) {
            try {
                JSONObject prefJson = new JSONObject(userPreferenceJson);
                if (prefJson.has("dietaryRestrictions")) {
                    JSONArray arr = prefJson.getJSONArray("dietaryRestrictions");
                    userPreference.setDietaryRestrictions(
                        IntStream.range(0, arr.length())
                            .mapToObj(arr::getString)
                            .toList());
                }
                if (prefJson.has("favoriteCuisines")) {
                    JSONArray arr = prefJson.getJSONArray("favoriteCuisines");
                    userPreference.setFavoriteCuisines(
                        IntStream.range(0, arr.length())
                            .mapToObj(arr::getString)
                            .toList());
                }
                if (prefJson.has("dislikedIngredients")) {
                    JSONArray arr = prefJson.getJSONArray("dislikedIngredients");
                    userPreference.setDislikedIngredients(
                        IntStream.range(0, arr.length())
                            .mapToObj(arr::getString)
                            .toList());
                }
            } catch (Exception e) {
                logger.warn("Failed to parse user preference, using defaults: {}", e.getMessage());
            }
        }
        return userPreference;
    }

    /**
     * Validates ingredients provided by the user.
     * Validates ingredient names, quantities, and units (metric/international units only).
     * Returns only valid ingredients that pass all validation checks.
     * 
     * @param request Request containing list of ingredients to validate
     * @return List of validated ingredients with metric units only
     */
    @PostMapping(value = "/validate-ingredients")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> validateAndParseIngredientsFromText(
            @RequestBody @Valid ManualIngredientDetectionRequest request) {

        if (request.getIngredients() == null || request.getIngredients().isEmpty()) {
            throw new BadRequestException("Ingredients list cannot be empty");
        }

        logger.info("Validating {} ingredients provided by user (metric units only)", request.getIngredients().size());
        List<DetectedIngredientDto> ingredients = ingredientDetectionService.validateAndParseIngredientsFromText(request.getIngredients());
        logger.info("Successfully validated {} ingredients", ingredients.size());
        
        SuccessResponseEntity<List<DetectedIngredientDto>> body = new SuccessResponseEntity<>();
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

    // Unsave recipe from user
    @DeleteMapping("/save")
    @PreAuthorize("hasRole('ADMIN') or #userRecipeCreateDto.userId == authentication.principal.id")
    public ResponseEntity<Void> unsaveRecipeFromUser(@RequestBody @Valid UserRecipeCreateDto userRecipeCreateDto) {
        logger.info("Unsaving recipe from user: {}", userRecipeCreateDto);
        recipeService.removeRecipeFromUser(userRecipeCreateDto.getUserId(), userRecipeCreateDto.getRecipeId());
        return ResponseEntity.noContent().build();
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
            @RequestParam(defaultValue = "6") int limit) {

        UUID userId = user.getId();
        List<RecipeReadDto> recommendedRecipes = recipeService.getRecommendedRecipes(userId, limit);

        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(recommendedRecipes);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/today-picks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> getTodayPicks(
            @AuthenticationPrincipal User user) {

        logger.info("Getting today's picks for user: {}", user.getId());
        UUID userId = user.getId();
        List<RecipeReadDto> todayPicks = recipeService.getTodayPicks(userId);

        SuccessResponseEntity<List<RecipeReadDto>> body = new SuccessResponseEntity<>();
        body.setData(todayPicks);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/saved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaginationResponse<UserRecipeReadDto>> getSavedRecipes(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "6") int limit) {

        UUID userId = user.getId();
        PaginationResponse<UserRecipeReadDto> savedRecipes = recipeService.getSavedRecipesByUserId(userId, limit, offset);

        return ResponseEntity.ok(savedRecipes);
    }

    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaginationResponse<RecipeReadDto>> getRecentGeneratedRecipes(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "6") int limit) {

        logger.info("Getting recent generated recipes for user: {}", user.getId());
        UUID userId = user.getId();
        PaginationResponse<RecipeReadDto> recentRecipes = recipeService.getRecentGeneratedRecipes(userId, limit, offset);

        return ResponseEntity.ok(recentRecipes);
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
            @RequestParam(required = false) String name,
            @RequestParam(required = false) List<String> cuisines,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) List<String> ingredients,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Integer maxTotalTime,
            @RequestParam(required = false) Integer minServings) {

        PaginationResponse<RecipeReadDto> response;

        // Check if any filter is applied
        boolean hasFilters = (name != null && !name.isEmpty()) ||
                           (cuisines != null && !cuisines.isEmpty()) ||
                           (tags != null && !tags.isEmpty()) ||
                           (ingredients != null && !ingredients.isEmpty()) ||
                           difficulty != null ||
                           maxTotalTime != null ||
                           minServings != null;

        if (hasFilters) {
            response = recipeService.getAllRecipes(limit, offset, name, cuisines, tags, ingredients,
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
