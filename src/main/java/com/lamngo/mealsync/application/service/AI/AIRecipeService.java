package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.presentation.error.AIServiceException; // New exception name
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class AIRecipeService {
    private static final Logger logger = LoggerFactory.getLogger(AIRecipeService.class);

    // OPENAI CONFIGURATION
    @Value("${OPENAI_API_BASE_URL}") // e.g., https://api.openai.com/v1/chat/completions
    private String openAIApiBaseUrl;

    @Value("${OPENAI_API_KEY}")
    private String openAIApiKey;

    private static final String GPT_MODEL = "gpt-4o-mini"; // Define the model
    
    private WebClient openAIWebClient;
    
    private final RecipeMapper recipeMapper;
    private final IRecipeRepo recipeRepo;
    private final PromptLoader promptLoader;
    private final TransactionTemplate transactionTemplate;
    
    // Cache for all recipes to avoid repeated database queries
    private volatile List<Recipe> cachedAllRecipes = null;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes cache TTL
    
    // Cache for recipe generation prompt to avoid file I/O
    private volatile String cachedRecipePrompt = null;

    public AIRecipeService(
            RecipeMapper recipeMapper,
            IRecipeRepo recipeRepo,
            PromptLoader promptLoader,
            PlatformTransactionManager transactionManager) {
        this.recipeMapper = recipeMapper;
        this.recipeRepo = recipeRepo;
        this.promptLoader = promptLoader;
        // Create TransactionTemplate for programmatic transaction management
        // This is needed because @Transactional doesn't work in reactive chains
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }
    
    @PostConstruct
    public void init() {
        // Initialize WebClient for async OpenAI API calls
        this.openAIWebClient = WebClient.builder()
                .baseUrl(openAIApiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + openAIApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    /**
     * Gets the cached recipe generation prompt, loading from cache if available.
     */
    private String getRecipePrompt() {
        if (cachedRecipePrompt == null) {
            synchronized (this) {
                if (cachedRecipePrompt == null) {
                    cachedRecipePrompt = promptLoader.loadPrompt("recipe-generation.txt");
                    logger.debug("Loaded and cached recipe generation prompt");
                }
            }
        }
        return cachedRecipePrompt;
    }

    // Generate recipes from a list of detected ingredients with quantities and units
    public List<RecipeReadDto> generateRecipes(List<DetectedIngredientDto> ingredients, UserPreference userPreference) {
        if (ingredients == null || ingredients.isEmpty()) {
            logger.warn("Ingredients list is empty or null");
            throw new AIServiceException("Ingredients list is empty or null");
        }
        if (userPreference == null) {
            userPreference = new UserPreference();
            logger.debug("No user preference provided, using default");
        }

        logger.info("Generating recipes from {} ingredients", ingredients.size());
        try {
            // Use async OpenAI call but block to get results immediately
            // This is still faster than blocking OkHttpClient because WebClient is more efficient
            List<RecipeReadDto> recipeDtos = fetchRecipesFromOpenAIAsync(ingredients, userPreference)
                    .join(); // Block here to get results, but WebClient handles connection pooling better
            
            logger.info("Successfully fetched {} recipes from OpenAI", recipeDtos.size());

            // Generate images completely asynchronously - don't wait (saves ~10s)
            addImagesToRecipesBatchAsync(recipeDtos);  // Fire and forget

            return recipeDtos;
        } catch (AIServiceException e) {
            logger.error("AI service error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error generating recipes: {}", e.getMessage(), e);
            throw new AIServiceException("Failed to generate recipes: " + e.getMessage());
        }
    }

    // Fetch recipes from OpenAI API using detected ingredients with quantities and units (async version)
    public CompletableFuture<List<RecipeReadDto>> fetchRecipesFromOpenAIAsync(
            List<DetectedIngredientDto> ingredients, UserPreference userPreference) {
        if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                || openAIApiKey == null || openAIApiKey.isEmpty()) {
            logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly. Check your env.properties file.");
            return CompletableFuture.failedFuture(new AIServiceException("OpenAI API configuration error."));
        }
        
        // Build ingredients string with quantities and units
        StringBuilder ingredientsStringBuilder = new StringBuilder();
        for (DetectedIngredientDto ing : ingredients) {
            if (ingredientsStringBuilder.length() > 0) {
                ingredientsStringBuilder.append(", ");
            }
            String ingStr = ing.getName();
            if (ing.getQuantity() != null && !ing.getQuantity().isEmpty() && !ing.getQuantity().equals("1")) {
                ingStr = ing.getQuantity() + " " + ingStr;
            }
            if (ing.getUnit() != null && !ing.getUnit().isEmpty()) {
                ingStr = ingStr + " (" + ing.getUnit() + ")";
            }
            ingredientsStringBuilder.append(ingStr);
        }
        String ingredientsString = ingredientsStringBuilder.toString();

        // Use cached prompt template and format it
        String promptTemplate = getRecipePrompt();
        String prompt = promptLoader.formatPrompt(promptTemplate, Map.of(
                "INGREDIENTS", ingredientsString,
                "DIETARY_RESTRICTIONS", userPreference.getDietaryRestrictions() != null 
                        ? String.join(", ", userPreference.getDietaryRestrictions()) : "",
                "FAVORITE_CUISINES", userPreference.getFavoriteCuisines() != null 
                        ? String.join(", ", userPreference.getFavoriteCuisines()) : "",
                "DISLIKED_INGREDIENTS", userPreference.getDislikedIngredients() != null 
                        ? String.join(", ", userPreference.getDislikedIngredients()) : ""
        ));

        // Construct the OpenAI Request Body
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", GPT_MODEL);
        requestBody.put("response_format", new JSONObject().put("type", "json_object"));
        requestBody.put("messages", new JSONArray()
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", prompt)));

        logger.info("Sending async request to OpenAI API using {}", GPT_MODEL);

        // Use WebClient for async call
        return openAIWebClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> {
                            return response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        logger.error("OpenAI API call failed: HTTP {} - Response: {}", response.statusCode(), errorBody);
                                        String errorMessage = "OpenAI API call failed: HTTP " + response.statusCode();
                                        try {
                                            JSONObject errorJson = new JSONObject(errorBody);
                                            if (errorJson.has("error") && errorJson.getJSONObject("error").has("message")) {
                                                errorMessage += " - " + errorJson.getJSONObject("error").getString("message");
                                            }
                                        } catch (Exception e) {
                                            // If parsing fails, use the raw error body
                                            if (errorBody != null && !errorBody.isEmpty()) {
                                                errorMessage += " - " + errorBody;
                                            }
                                        }
                                        return Mono.error(new AIServiceException(errorMessage));
                                    });
                        })
                .bodyToMono(String.class)
                .map(responseBody -> {
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (!json.has("choices") || json.getJSONArray("choices").isEmpty()) {
                            logger.error("No choices returned from OpenAI API");
                            throw new AIServiceException("No choices returned from OpenAI API");
                        }

                        JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        String recipesJson = message.getString("content").trim();

                        logger.debug("Raw OpenAI response content: {}", recipesJson);

                        JSONObject contentObject = new JSONObject(recipesJson);
                        if (!contentObject.has("recipes")) {
                            logger.error("Response does not contain 'recipes' array. Response: {}", recipesJson);
                            throw new AIServiceException("Invalid response format: missing 'recipes' array");
                        }
                        JSONArray recipesArray = contentObject.getJSONArray("recipes");

                        // Use TransactionTemplate to ensure database operations run in a transaction
                        // This is necessary because @Transactional doesn't work in reactive chains
                        return transactionTemplate.execute(status -> parseAndSaveRecipes(recipesArray));
                    } catch (JSONException e) {
                        logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                        throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                    }
                })
                .doOnError(error -> {
                    logger.error("Error fetching recipes from OpenAI API", error);
                })
                .onErrorResume(throwable -> {
                    AIServiceException exception;
                    if (throwable instanceof AIServiceException) {
                        exception = (AIServiceException) throwable;
                    } else {
                        String errorMsg = throwable != null ? throwable.getMessage() : "Unknown error";
                        exception = new AIServiceException("Error fetching recipes from OpenAI API: " + errorMsg);
                    }
                    return Mono.error(exception);
                })
                .toFuture();
    }
    
    /**
     * Parses and saves recipes from JSON array.
     * Note: This method should be called within a transaction context (via TransactionTemplate).
     * The @Transactional annotation is kept for safety but may not work in reactive chains.
     */
    @Transactional
    private List<RecipeReadDto> parseAndSaveRecipes(JSONArray recipesArray) {
        List<Recipe> recipes = new ArrayList<>();
        List<Recipe> newRecipesToSave = new ArrayList<>();
        
        // Pre-compute all ingredient keys to batch lookup
        Map<String, String> recipeNameToIngredientKey = new HashMap<>();
        for (int i = 0; i < recipesArray.length(); i++) {
            JSONObject obj = recipesArray.getJSONObject(i);
            String recipeName = obj.optString("name", null);
            if (recipeName != null && !recipeName.trim().isEmpty()) {
                String ingredientKey = generateIngredientKey(recipeName);
                if (ingredientKey != null && !ingredientKey.isBlank()) {
                    recipeNameToIngredientKey.put(recipeName, ingredientKey);
                }
            }
        }
        
        // Batch lookup all ingredient keys (sequential for thread safety with DB)
        Map<String, Optional<Recipe>> ingredientKeyToRecipe = new HashMap<>();
        for (String ingredientKey : recipeNameToIngredientKey.values().stream().distinct().toList()) {
            ingredientKeyToRecipe.put(ingredientKey, recipeRepo.findByIngredientKey(ingredientKey));
        }

        for (int i = 0; i < recipesArray.length(); i++) {
            JSONObject obj = recipesArray.getJSONObject(i);
            String recipeName = obj.optString("name", null);
            
            if (recipeName == null || recipeName.trim().isEmpty()) {
                logger.warn("Skipping recipe due to missing name");
                continue;
            }

            // First check for similar recipe by name (90% similarity threshold)
            Optional<Recipe> similarRecipeOpt = findSimilarRecipe(recipeName);
            Recipe recipe;
            
            if (similarRecipeOpt.isPresent()) {
                // Re-fetch the recipe from database to ensure it's attached to current session
                // This prevents lazy loading issues with collections
                Recipe cachedRecipe = similarRecipeOpt.get();
                Optional<Recipe> attachedRecipe = recipeRepo.getRecipeById(cachedRecipe.getId());
                if (attachedRecipe.isPresent()) {
                    recipe = attachedRecipe.get();
                    logger.debug("Found similar recipe '{}' for '{}'. Using existing recipe from database.", 
                               recipe.getName(), recipeName);
                } else {
                    // Fallback to cached recipe if re-fetch fails (shouldn't happen)
                    recipe = cachedRecipe;
                    logger.warn("Could not re-fetch recipe {} from database, using cached version", cachedRecipe.getId());
                }
            } else {
                // Check by ingredientKey using pre-fetched results
                String ingredientKey = recipeNameToIngredientKey.get(recipeName);

                if (ingredientKey == null || ingredientKey.isBlank()) {
                    logger.warn("Skipping recipe due to missing ingredientKey");
                    continue;
                }

                Optional<Recipe> existingRecipeOpt = ingredientKeyToRecipe.get(ingredientKey);
                if (existingRecipeOpt != null && existingRecipeOpt.isPresent()) {
                    // Recipe from findByIngredientKey should already be attached to session
                    // but we ensure collections are initialized later
                    recipe = existingRecipeOpt.get();
                    logger.debug("Recipe with ingredientKey '{}' already exists. Using existing recipe.", ingredientKey);
                } else {
                    recipe = buildRecipeFromJson(obj, recipeName, ingredientKey);
                    newRecipesToSave.add(recipe);
                }
            }
            recipes.add(recipe);
        }
        
        // Batch save all new recipes at once (more efficient)
        if (!newRecipesToSave.isEmpty()) {
            logger.info("Batch saving {} new recipes to database", newRecipesToSave.size());
            for (Recipe newRecipe : newRecipesToSave) {
                Recipe savedRecipe = recipeRepo.createRecipe(newRecipe);
                // Update the recipes list with saved entity
                for (int i = 0; i < recipes.size(); i++) {
                    if (recipes.get(i) == newRecipe) {
                        recipes.set(i, savedRecipe);
                        break;
                    }
                }
            }
        }

        // Initialize lazy-loaded collections before mapping to DTOs
        // This prevents "could not initialize proxy - no Session" errors
        for (Recipe recipe : recipes) {
            if (recipe != null) {
                // Force initialization of lazy collections
                if (recipe.getTags() != null) {
                    recipe.getTags().size(); // Access collection to initialize
                }
                if (recipe.getIngredients() != null) {
                    recipe.getIngredients().size(); // Access collection to initialize
                }
            }
        }

        return recipeMapper.toRecipeReadDtoList(recipes);
    }
    
    private Recipe buildRecipeFromJson(JSONObject obj, String recipeName, String ingredientKey) {
        Recipe recipe = new Recipe();
        recipe.setName(recipeName);

        List<String> instructions = new ArrayList<>();
        if (obj.has("instructions") && !obj.isNull("instructions")) {
            JSONArray instArray = obj.getJSONArray("instructions");
            for (int k = 0; k < instArray.length(); k++) {
                instructions.add(instArray.optString(k, ""));
            }
        }
        recipe.setInstructions(instructions);
        recipe.setCuisine(obj.optString("cuisine"));
        recipe.setIngredientKey(ingredientKey);
        recipe.setDescription(obj.optString("description", ""));
        recipe.setPreparationTime(obj.optInt("preparationTime", 0));
        recipe.setCookingTime(obj.optInt("cookingTime", 0));
        recipe.setTotalTime(obj.optInt("totalTime", 0));
        recipe.setServings(obj.optInt("servings", 1));
        recipe.setCalories(obj.optDouble("calories", 0.0));
        recipe.setProtein(obj.optDouble("protein", 0.0));
        recipe.setCarbohydrates(obj.optDouble("carbohydrates", 0.0));
        recipe.setFat(obj.optDouble("fat", 0.0));
        recipe.setDifficulty(obj.optString("difficulty", ""));

        // Tags
        List<String> tags = new ArrayList<>();
        if (obj.has("tags") && !obj.isNull("tags")) {
            JSONArray tagsArray = obj.getJSONArray("tags");
            for (int t = 0; t < tagsArray.length(); t++) {
                tags.add(tagsArray.optString(t, ""));
            }
        }
        recipe.setTags(tags);

        // Ingredients
        if (obj.has("ingredients") && !obj.isNull("ingredients")) {
            JSONArray ingredientsArray = obj.getJSONArray("ingredients");
            List<RecipeIngredient> ingredientList = new ArrayList<>();
            for (int j = 0; j < ingredientsArray.length(); j++) {
                JSONObject ingObj = ingredientsArray.getJSONObject(j);
                RecipeIngredient ingredient = new RecipeIngredient();
                ingredient.setName(ingObj.optString("name"));
                ingredient.setQuantity(ingObj.optString("quantity", "1"));
                ingredient.setUnit(ingObj.optString("unit", ""));
                ingredient.setRecipe(recipe);
                ingredientList.add(ingredient);
            }
            recipe.setIngredients(ingredientList);
        }
        
        return recipe;
    }

    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     * Use RecipeImageService.generateImagesForRecipes() instead.
     */
    @Deprecated
    public void addImageToRecipe(RecipeReadDto dto) {
        logger.warn("addImageToRecipe is deprecated. Use RecipeImageService instead.");
        // This method is kept for backward compatibility but should not be used
    }

    public String generateIngredientKey(String recipeName) {
        if (recipeName == null) return null;
        return recipeName.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "_");
    }

    /**
     * Calculates the similarity between two strings using Levenshtein distance.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        String lower1 = s1.toLowerCase().trim();
        String lower2 = s2.toLowerCase().trim();
        
        if (lower1.equals(lower2)) {
            return 1.0;
        }
        
        int maxLength = Math.max(lower1.length(), lower2.length());
        if (maxLength == 0) {
            return 1.0;
        }
        
        int distance = levenshteinDistance(lower1, lower2);
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculates Levenshtein distance between two strings.
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    /**
     * Finds a similar recipe in the database by name.
     * Returns the most similar recipe if similarity is >= 0.9 (90%), otherwise returns empty.
     * Uses cached recipe list for better performance.
     * Optimized with early exits and length filtering.
     */
    private Optional<Recipe> findSimilarRecipe(String recipeName) {
        if (recipeName == null || recipeName.trim().isEmpty()) {
            return Optional.empty();
        }
        
        List<Recipe> allRecipes = getCachedAllRecipes();
        if (allRecipes.isEmpty()) {
            return Optional.empty();
        }
        
        String normalizedName = recipeName.toLowerCase().trim();
        int normalizedLength = normalizedName.length();
        double maxSimilarity = 0.0;
        Recipe mostSimilarRecipe = null;
        
        // Early exit optimization: if exact match found, return immediately
        for (Recipe recipe : allRecipes) {
            if (recipe.getName() == null) {
                continue;
            }
            
            String recipeNameLower = recipe.getName().toLowerCase().trim();
            
            // Fast exact match check
            if (normalizedName.equals(recipeNameLower)) {
                logger.debug("Found exact match for recipe '{}'", recipeName);
                return Optional.of(recipe);
            }
            
            // Only calculate similarity if names are similar length (optimization)
            int lengthDiff = Math.abs(normalizedLength - recipeNameLower.length());
            if (lengthDiff > normalizedLength * 0.3) {
                continue; // Skip if length difference is too large (>30%)
            }
            
            // Quick prefix/suffix check before expensive similarity calculation
            if (normalizedLength > 5 && recipeNameLower.length() > 5) {
                if (!normalizedName.substring(0, Math.min(3, normalizedLength))
                        .equals(recipeNameLower.substring(0, Math.min(3, recipeNameLower.length())))) {
                    continue; // Skip if first 3 chars don't match
                }
            }
            
            double similarity = calculateSimilarity(normalizedName, recipeNameLower);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                mostSimilarRecipe = recipe;
                
                // Early exit if we found a perfect match
                if (maxSimilarity >= 1.0) {
                    break;
                }
            }
        }
        
        // Return recipe if similarity is >= 90% (0.9)
        if (maxSimilarity >= 0.9 && mostSimilarRecipe != null) {
            logger.debug("Found similar recipe '{}' with {:.2f}% similarity to '{}'", 
                       mostSimilarRecipe.getName(), maxSimilarity * 100, recipeName);
            return Optional.of(mostSimilarRecipe);
        }
        
        return Optional.empty();
    }
    
    /**
     * Gets cached list of all recipes, refreshing if cache is stale.
     */
    private List<Recipe> getCachedAllRecipes() {
        long currentTime = System.currentTimeMillis();
        if (cachedAllRecipes == null || (currentTime - cacheTimestamp) > CACHE_TTL_MS) {
            synchronized (this) {
                // Double-check locking pattern
                if (cachedAllRecipes == null || (currentTime - cacheTimestamp) > CACHE_TTL_MS) {
                    logger.debug("Refreshing cached recipes list");
                    cachedAllRecipes = recipeRepo.findAllRecipes();
                    cacheTimestamp = currentTime;
                }
            }
        }
        return cachedAllRecipes;
    }
    
    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     * Use RecipeImageService.generateImagesForRecipes() instead.
     */
    @Deprecated
    public CompletableFuture<Void> addImagesToRecipesBatch(List<RecipeReadDto> recipeDtos) {
        logger.warn("addImagesToRecipesBatch is deprecated. Use RecipeImageService instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     * Use RecipeImageService.generateImagesForRecipes() instead.
     */
    @Deprecated
    public CompletableFuture<Void> addImagesToRecipesBatchAsync(List<RecipeReadDto> recipeDtos) {
        logger.warn("addImagesToRecipesBatchAsync is deprecated. Use RecipeImageService instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated Image generation has been moved to ImageGenerationService.
     * Use ImageGenerationService.generateImagesForRecipes() with Spring Events instead.
     */
    @Deprecated
    public CompletableFuture<Void> addImagesToRecipesBatchAsync(
            List<RecipeReadDto> recipeDtos, 
            Object onImageReady) {
        logger.warn("addImagesToRecipesBatchAsync with callback is deprecated. Use RecipeImageService with Spring Events instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated Image generation has been moved to RecipeImageService.
     */
    @Deprecated
    public CompletableFuture<Void> addImageToRecipeAsync(RecipeReadDto dto) {
        logger.warn("addImageToRecipeAsync is deprecated. Use RecipeImageService instead.");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * @deprecated User history saving has been moved to RecipeGenerationOrchestrator.
     */
    @Deprecated
    public CompletableFuture<Void> saveGeneratedRecipesToUserAsync(UUID userId, List<UUID> recipeIds) {
        logger.warn("saveGeneratedRecipesToUserAsync is deprecated. Use RecipeGenerationOrchestrator instead.");
        return CompletableFuture.completedFuture(null);
    }
}