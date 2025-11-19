package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.presentation.error.AIServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-based recipe generation service.
 * Uses gpt-4o-mini for generating recipes from ingredients.
 */
@Service("openAIRecipeService")
public class OpenAIRecipeService implements IRecipeGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIRecipeService.class);

    @Value("${OPENAI_API_BASE_URL}")
    private String openAIApiBaseUrl;

    @Value("${OPENAI_API_KEY}")
    private String openAIApiKey;

    private static final String GPT_MODEL = "gpt-4o-mini";
    private static final int OPENAI_MAX_COMPLETION_TOKENS = 1500;

    private WebClient openAIWebClient;

    private final RecipeMapper recipeMapper;
    private final IRecipeRepo recipeRepo;
    private final PromptLoader promptLoader;
    private final TransactionTemplate transactionTemplate;

    // Cache for recipe generation prompt to avoid file I/O
    private volatile String cachedRecipePrompt = null;

    public OpenAIRecipeService(
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
        // Initialize WebClient for async OpenAI API calls with optimized connection
        // settings
        this.openAIWebClient = WebClient.builder()
                .baseUrl(openAIApiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + openAIApiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
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

    @Override
    public CompletableFuture<List<RecipeReadDto>> generateRecipesAsync(
            List<DetectedIngredientDto> ingredients,
            UserPreference userPreference) {
        // Parallelize generation with 3 distinct styles to ensure diversity and speed
        // Each request generates 1 recipe, running concurrently

        CompletableFuture<List<RecipeReadDto>> future1 = generateSingleRecipeInternal(
                ingredients, userPreference,
                "Style: The 'Speed' Option. Focus strictly on speed (under 20 mins). Use shortcuts, one-pot methods, or minimal prep. The goal is getting food on the table fast.")
                .exceptionally(ex -> {
                    logger.error("Failed to get recipe variation 1", ex);
                    return null;
                });

        CompletableFuture<List<RecipeReadDto>> future2 = generateSingleRecipeInternal(
                ingredients, userPreference,
                "Style: The 'Chef's Special'. Focus on culinary technique, presentation, and unique flavor pairings. Ignore time constraints. Make it impressive.")
                .exceptionally(ex -> {
                    logger.error("Failed to get recipe variation 2", ex);
                    return null;
                });

        CompletableFuture<List<RecipeReadDto>> future3 = generateSingleRecipeInternal(
                ingredients, userPreference,
                "Style: The 'Nourish' Option. Focus on maximizing micronutrients and whole foods. Use gentle cooking methods (steaming, poaching, raw) or nutrient-dense combinations.")
                .exceptionally(ex -> {
                    logger.error("Failed to get recipe variation 3", ex);
                    return null;
                });

        return CompletableFuture.allOf(future1, future2, future3)
                .thenApply(v -> {
                    List<RecipeReadDto> allRecipes = new ArrayList<>();
                    try {
                        List<RecipeReadDto> r1 = future1.join();
                        if (r1 != null) {
                            logger.info("Variation 1 (Quick) returned {} recipes", r1.size());
                            allRecipes.addAll(r1);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to get recipe variation 1", e);
                    }

                    try {
                        List<RecipeReadDto> r2 = future2.join();
                        if (r2 != null) {
                            logger.info("Variation 2 (Creative) returned {} recipes", r2.size());
                            allRecipes.addAll(r2);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to get recipe variation 2", e);
                    }

                    try {
                        List<RecipeReadDto> r3 = future3.join();
                        if (r3 != null) {
                            logger.info("Variation 3 (Healthy) returned {} recipes", r3.size());
                            allRecipes.addAll(r3);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to get recipe variation 3", e);
                    }

                    logger.info("Total recipes before deduplication: {}", allRecipes.size());

                    // Deduplicate by name, but if duplicate exists, append style to make it unique
                    Map<String, RecipeReadDto> uniqueRecipes = new LinkedHashMap<>(); // Use LinkedHashMap to preserve
                                                                                      // order

                    if (future1.join() != null) {
                        for (RecipeReadDto r : future1.join()) {
                            if (uniqueRecipes.containsKey(r.getName())) {
                                r.setName(r.getName() + " (Quick & Easy)");
                            }
                            uniqueRecipes.put(r.getName(), r);
                        }
                    }

                    if (future2.join() != null) {
                        for (RecipeReadDto r : future2.join()) {
                            if (uniqueRecipes.containsKey(r.getName())) {
                                r.setName(r.getName() + " (Creative Twist)");
                            }
                            uniqueRecipes.put(r.getName(), r);
                        }
                    }

                    if (future3.join() != null) {
                        for (RecipeReadDto r : future3.join()) {
                            if (uniqueRecipes.containsKey(r.getName())) {
                                r.setName(r.getName() + " (Healthy Option)");
                            }
                            uniqueRecipes.put(r.getName(), r);
                        }
                    }

                    logger.info("Total recipes after smart deduplication: {}", uniqueRecipes.size());
                    return new ArrayList<>(uniqueRecipes.values());
                });
    }

    private CompletableFuture<List<RecipeReadDto>> generateSingleRecipeInternal(
            List<DetectedIngredientDto> ingredients,
            UserPreference userPreference,
            String styleInstruction) {
        if (ingredients == null || ingredients.isEmpty()) {
            logger.warn("Ingredients list is empty or null");
            return CompletableFuture.failedFuture(new AIServiceException("Ingredients list is empty or null"));
        }
        if (userPreference == null) {
            userPreference = new UserPreference();
            logger.debug("No user preference provided, using default");
        }

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
                        ? String.join(", ", userPreference.getDietaryRestrictions())
                        : "",
                "FAVORITE_CUISINES", userPreference.getFavoriteCuisines() != null
                        ? String.join(", ", userPreference.getFavoriteCuisines())
                        : "",
                "DISLIKED_INGREDIENTS", userPreference.getDislikedIngredients() != null
                        ? String.join(", ", userPreference.getDislikedIngredients())
                        : "",
                "STYLE", styleInstruction));

        // Construct the OpenAI Request Body with performance optimizations
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", GPT_MODEL);
        requestBody.put("temperature", 0.4); // Slightly higher for creativity with styles
        requestBody.put("max_completion_tokens", OPENAI_MAX_COMPLETION_TOKENS);
        requestBody.put("response_format", new JSONObject().put("type", "json_object"));
        requestBody.put("messages", new JSONArray()
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", prompt)));

        String payload = requestBody.toString();
        long requestStartNs = System.nanoTime();
        logger.debug(
                "Sending async request to OpenAI API using {} (temperature=0.4, ingredients={}, maxCompletionTokens={}, payload={} chars)",
                GPT_MODEL, ingredients.size(), OPENAI_MAX_COMPLETION_TOKENS, payload.length());

        // Use WebClient for async call with detailed timing logs
        return openAIWebClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    long headersNs = System.nanoTime();
                    long ttfbMs = Duration.ofNanos(headersNs - requestStartNs).toMillis();
                    logger.debug("OpenAI responded with status {} after {} ms", response.statusCode(), ttfbMs);

                    Mono<String> bodyMono = response.bodyToMono(String.class);

                    if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                        return bodyMono.flatMap(errorBody -> {
                            String errorMessage = "OpenAI API call failed: HTTP " + response.statusCode();
                            try {
                                JSONObject errorJson = new JSONObject(errorBody);
                                if (errorJson.has("error") && errorJson.getJSONObject("error").has("message")) {
                                    errorMessage += " - " + errorJson.getJSONObject("error").getString("message");
                                }
                            } catch (Exception e) {
                                if (errorBody != null && !errorBody.isEmpty()) {
                                    errorMessage += " - " + errorBody;
                                }
                            }
                            logger.error("{} (payload {} chars)", errorMessage,
                                    errorBody != null ? errorBody.length() : 0);
                            return Mono.error(new AIServiceException(errorMessage));
                        });
                    }
                    return bodyMono;
                })
                .map(responseBody -> {
                    long parseStartNs = System.nanoTime();
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
                        long persistStartNs = System.nanoTime();
                        List<RecipeReadDto> dtos = transactionTemplate
                                .execute(status -> parseAndSaveRecipes(recipesArray));
                        long persistDurationMs = Duration.ofNanos(System.nanoTime() - persistStartNs).toMillis();
                        long parseDurationMs = Duration.ofNanos(System.nanoTime() - parseStartNs).toMillis();
                        logger.info("OpenAI single recipe parsed in {} ms, DB persist {} ms", parseDurationMs,
                                persistDurationMs);
                        return dtos;
                    } catch (JSONException e) {
                        logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                        throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                    }
                })
                .doOnError(error -> {
                    logger.error("Error fetching recipe from OpenAI API", error);
                })
                .onErrorResume(throwable -> {
                    AIServiceException exception;
                    if (throwable instanceof AIServiceException) {
                        exception = (AIServiceException) throwable;
                    } else {
                        String errorMsg = throwable != null ? throwable.getMessage() : "Unknown error";
                        exception = new AIServiceException("Error fetching recipe from OpenAI API: " + errorMsg);
                    }
                    return Mono.error(exception);
                })
                .toFuture();
    }

    /**
     * Parses and saves recipes from JSON array.
     * Note: This method should be called within a transaction context (via
     * TransactionTemplate).
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

        // Batch lookup all ingredient keys in a single query (much faster)
        List<String> distinctIngredientKeys = recipeNameToIngredientKey.values().stream()
                .distinct()
                .toList();
        Map<String, Optional<Recipe>> ingredientKeyToRecipe = recipeRepo
                .findByIngredientKeysBatch(distinctIngredientKeys);

        for (int i = 0; i < recipesArray.length(); i++) {
            JSONObject obj = recipesArray.getJSONObject(i);
            String recipeName = obj.optString("name", null);

            if (recipeName == null || recipeName.trim().isEmpty()) {
                logger.warn("Skipping recipe due to missing name");
                continue;
            }

            // Step 1: Fast ingredientKey check (catches exact/similar matches)
            String ingredientKey = recipeNameToIngredientKey.get(recipeName);
            if (ingredientKey == null || ingredientKey.isBlank()) {
                logger.warn("Skipping recipe due to missing ingredientKey");
                continue;
            }

            Optional<Recipe> existingRecipeOpt = ingredientKeyToRecipe.get(ingredientKey);
            Recipe recipe;

            if (existingRecipeOpt != null && existingRecipeOpt.isPresent()) {
                // Found by ingredientKey - reuse it
                recipe = existingRecipeOpt.get();
                logger.debug("Recipe with ingredientKey '{}' already exists. Using existing recipe.", ingredientKey);
            } else {
                // Step 2: Fast PostgreSQL fuzzy search (catches word-order/extra-word
                // variations)
                // Only check similarity if ingredientKey doesn't match (rare case)
                // Uses PostgreSQL pg_trgm extension - much faster than loading all recipes
                Optional<Recipe> similarRecipe = recipeRepo.findSimilarRecipeByName(recipeName, 0.85);

                if (similarRecipe.isPresent()) {
                    // Found similar recipe - reuse it (AVOIDS DUPLICATE)
                    recipe = similarRecipe.get();
                    logger.debug(
                            "Found similar recipe '{}' for '{}' using PostgreSQL fuzzy matching. Reusing to avoid duplicate.",
                            recipe.getName(), recipeName);
                } else {
                    // Step 3: New recipe - create it
                    recipe = buildRecipeFromJson(obj, recipeName, ingredientKey);
                    newRecipesToSave.add(recipe);
                }
            }
            recipes.add(recipe);
        }

        // Batch save all new recipes in a single transaction (much faster)
        if (!newRecipesToSave.isEmpty()) {
            logger.info("Batch saving {} new recipes to database", newRecipesToSave.size());
            List<Recipe> savedRecipes = recipeRepo.saveAllRecipes(newRecipesToSave);

            // Update the recipes list with saved entities
            int savedIndex = 0;
            for (int i = 0; i < recipes.size(); i++) {
                Recipe currentRecipe = recipes.get(i);
                if (newRecipesToSave.contains(currentRecipe)) {
                    recipes.set(i, savedRecipes.get(savedIndex++));
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

    public String generateIngredientKey(String recipeName) {
        if (recipeName == null)
            return null;
        return recipeName.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "_");
    }
}
