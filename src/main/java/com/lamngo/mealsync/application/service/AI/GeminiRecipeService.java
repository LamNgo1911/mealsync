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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Gemini-based recipe generation service.
 * Uses gemini-2.5-flash for generating recipes from ingredients.
 */
@Service("geminiRecipeService")
public class GeminiRecipeService implements IRecipeGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiRecipeService.class);

    @Value("${GEMINI_API_KEY}")
    private String geminiApiKey;

    @Value("${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com}")
    private String geminiApiBaseUrl;

    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    
    private WebClient geminiWebClient;
    
    private final RecipeMapper recipeMapper;
    private final IRecipeRepo recipeRepo;
    private final PromptLoader promptLoader;
    private final TransactionTemplate transactionTemplate;
    
    // Cache for recipe generation prompt to avoid file I/O
    private volatile String cachedRecipePrompt = null;

    public GeminiRecipeService(
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
        // Initialize WebClient for async Gemini API calls with optimized connection settings
        this.geminiWebClient = WebClient.builder()
                .baseUrl(geminiApiBaseUrl)
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
        if (ingredients == null || ingredients.isEmpty()) {
            logger.warn("Ingredients list is empty or null");
            return CompletableFuture.failedFuture(new AIServiceException("Ingredients list is empty or null"));
        }
        if (userPreference == null) {
            userPreference = new UserPreference();
            logger.debug("No user preference provided, using default");
        }

        if (geminiApiBaseUrl == null || geminiApiKey == null || geminiApiKey.isEmpty()) {
            logger.error("GEMINI_API_BASE_URL or GEMINI_API_KEY is not set properly. Check your env.properties file.");
            return CompletableFuture.failedFuture(new AIServiceException("Gemini API configuration error."));
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

        // Construct the Gemini Request Body
        JSONObject requestBody = new JSONObject();
        JSONArray contentsArray = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);
        content.put("parts", parts);
        contentsArray.put(content);
        requestBody.put("contents", contentsArray);
        
        // Generation config for faster, more deterministic responses
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.3); // Lower temperature = faster, more deterministic responses
        generationConfig.put("maxOutputTokens", 2000); // Limit response size to reduce generation time
        generationConfig.put("responseMimeType", "application/json"); // Request JSON response
        requestBody.put("generationConfig", generationConfig);

        String endpoint = String.format("/v1beta/models/%s:generateContent?key=%s", GEMINI_MODEL, geminiApiKey);

        logger.info("Sending async request to Gemini API using {} (temperature=0.3, maxOutputTokens=2000)", GEMINI_MODEL);

        // Use WebClient for async call
        return geminiWebClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> {
                            return response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        logger.error("Gemini API call failed: HTTP {} - Response: {}", response.statusCode(), errorBody);
                                        String errorMessage = "Gemini API call failed: HTTP " + response.statusCode();
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
                        if (!json.has("candidates") || json.getJSONArray("candidates").isEmpty()) {
                            logger.error("No candidates returned from Gemini API");
                            throw new AIServiceException("No candidates returned from Gemini API");
                        }

                        JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                        if (!candidate.has("content") || !candidate.getJSONObject("content").has("parts")) {
                            logger.error("Invalid response structure from Gemini API");
                            throw new AIServiceException("Invalid response structure from Gemini API");
                        }

                        JSONObject responseContent = candidate.getJSONObject("content");
                        JSONArray responseParts = responseContent.getJSONArray("parts");
                        if (responseParts.isEmpty() || !responseParts.getJSONObject(0).has("text")) {
                            logger.error("No text content in Gemini API response");
                            throw new AIServiceException("No text content in Gemini API response");
                        }

                        String recipesJson = responseParts.getJSONObject(0).getString("text").trim();

                        logger.debug("Raw Gemini response content: {}", recipesJson);

                        // Remove markdown code blocks if present (Gemini sometimes wraps JSON in ```json ... ```)
                        if (recipesJson.startsWith("```")) {
                            int startIdx = recipesJson.indexOf("{");
                            int endIdx = recipesJson.lastIndexOf("}");
                            if (startIdx > 0 && endIdx > startIdx) {
                                recipesJson = recipesJson.substring(startIdx, endIdx + 1);
                            }
                        }

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
                        logger.error("Failed to parse Gemini API response: {}", e.getMessage(), e);
                        throw new AIServiceException("Failed to parse Gemini API response: " + e.getMessage());
                    }
                })
                .doOnError(error -> {
                    logger.error("Error fetching recipes from Gemini API", error);
                })
                .onErrorResume(throwable -> {
                    AIServiceException exception;
                    if (throwable instanceof AIServiceException) {
                        exception = (AIServiceException) throwable;
                    } else {
                        String errorMsg = throwable != null ? throwable.getMessage() : "Unknown error";
                        exception = new AIServiceException("Error fetching recipes from Gemini API: " + errorMsg);
                    }
                    return Mono.error(exception);
                })
                .toFuture();
    }
    
    /**
     * Parses and saves recipes from JSON array.
     * Note: This method should be called within a transaction context (via TransactionTemplate).
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
        Map<String, Optional<Recipe>> ingredientKeyToRecipe = recipeRepo.findByIngredientKeysBatch(distinctIngredientKeys);

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
                // Step 2: Fast PostgreSQL fuzzy search (catches word-order/extra-word variations)
                // Only check similarity if ingredientKey doesn't match (rare case)
                // Uses PostgreSQL pg_trgm extension - much faster than loading all recipes
                Optional<Recipe> similarRecipe = recipeRepo.findSimilarRecipeByName(recipeName, 0.85);
                
                if (similarRecipe.isPresent()) {
                    // Found similar recipe - reuse it (AVOIDS DUPLICATE)
                    recipe = similarRecipe.get();
                    logger.debug("Found similar recipe '{}' for '{}' using PostgreSQL fuzzy matching. Reusing to avoid duplicate.", 
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
        if (recipeName == null) return null;
        return recipeName.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "_");
    }
}

