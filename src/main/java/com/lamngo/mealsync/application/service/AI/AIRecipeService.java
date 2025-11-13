package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.presentation.error.AIServiceException; // New exception name
import com.lamngo.mealsync.presentation.error.GeminiServiceException;
import jakarta.transaction.Transactional;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Increased timeout for multimodal
            .readTimeout(60, TimeUnit.SECONDS)   // Increased read timeout for multimodal
            .build();

    private final RecipeMapper recipeMapper;
    private final IRecipeRepo recipeRepo;
    private final ImageGeneratorService imageGeneratorService;
    private final S3Service s3Service;
    private final PromptLoader promptLoader;

    // Remove IRecipeIngredient if it's unused in the constructor
    public AIRecipeService(
            RecipeMapper recipeMapper,
            IRecipeRepo recipeRepo,
            // IRecipeIngredient recipeIngredientRepo, // Removed if not needed
            ImageGeneratorService imageGeneratorService,
            S3Service s3Service,
            PromptLoader promptLoader) {
        this.recipeMapper = recipeMapper;
        this.recipeRepo = recipeRepo;
        this.imageGeneratorService = imageGeneratorService;
        this.s3Service = s3Service;
        this.promptLoader = promptLoader;
    }

    // Generate recipes from a list of detected ingredients with quantities and units
    @Transactional
    public List<RecipeReadDto> generateRecipes(List<DetectedIngredientDto> ingredients, UserPreference userPreference) {
        System.out.println("Ingredients: " + ingredients);
        System.out.println("User preference: " + userPreference);

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
            List<RecipeReadDto> recipeDtos = fetchRecipesFromOpenAI(ingredients, userPreference);
            logger.info("Successfully fetched {} recipes from OpenAI", recipeDtos.size());

            for (RecipeReadDto dto : recipeDtos) {
                try {
                    addImageToRecipe(dto);
                    logger.debug("Successfully added image to recipe: {}", dto.getName());
                } catch (Exception e) {
                    logger.error("Failed to add image to recipe {}: {}", dto.getName(), e.getMessage(), e);
                    // Continue with other recipes even if one fails
                }
            }

            return recipeDtos;
        } catch (AIServiceException e) {
            logger.error("AI service error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error generating recipes: {}", e.getMessage(), e);
            throw new AIServiceException("Failed to generate recipes: " + e.getMessage());
        }
    }

    // Fetch recipes from OpenAI API using detected ingredients with quantities and units
    public List<RecipeReadDto> fetchRecipesFromOpenAI(List<DetectedIngredientDto> ingredients, UserPreference userPreference) {
        try {
            if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                    || openAIApiKey == null || openAIApiKey.isEmpty() ) {
                logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly. Check your env.properties file.");
                throw new AIServiceException("OpenAI API configuration error.");
            }
            System.out.println("Ingredients: " + ingredients);
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

            // Load and format the recipe generation prompt
            String prompt = promptLoader.loadAndFormatPrompt("recipe-generation.txt", Map.of(
                    "INGREDIENTS", ingredientsString,
                    "DIETARY_RESTRICTIONS", userPreference.getDietaryRestrictions() != null 
                            ? String.join(", ", userPreference.getDietaryRestrictions()) : "",
                    "FAVORITE_CUISINES", userPreference.getFavoriteCuisines() != null 
                            ? String.join(", ", userPreference.getFavoriteCuisines()) : "",
                    "DISLIKED_INGREDIENTS", userPreference.getDislikedIngredients() != null 
                            ? String.join(", ", userPreference.getDislikedIngredients()) : ""
            ));


            // Construct the OpenAI Request Body (text-only, no image)
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GPT_MODEL);
            requestBody.put("response_format", new JSONObject().put("type", "json_object"));

            // Build simple text message
            requestBody.put("messages", new JSONArray()
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("content", prompt)));


            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
            );

            String fullUrl = openAIApiBaseUrl; // OpenAI usually has one base URL
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(body)
                    .header("Authorization", "Bearer " + openAIApiKey) // Use API Key in header
                    .build();

            logger.info("Sending request to OpenAI API using {}", GPT_MODEL);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : response.message();
                    logger.error("OpenAI API call failed: HTTP {} - {}", response.code(), errorBody);
                    throw new AIServiceException("OpenAI API call failed: HTTP " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null) {
                    logger.error("Empty response from OpenAI API");
                    throw new AIServiceException("Empty response from OpenAI API");
                }

                try {
                    JSONObject json = new JSONObject(responseBody);
                    if (!json.has("choices") || json.getJSONArray("choices").isEmpty()) {
                        logger.error("No choices returned from OpenAI API");
                        throw new AIServiceException("No choices returned from OpenAI API");
                    }

                    JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");
                    String recipesJson = message.getString("content").trim();

                    // Log the raw response for debugging
                    logger.debug("Raw OpenAI response content: {}", recipesJson);

                    // Since we're using response_format: "json_object", the response
                    // will be a JSON object containing a "recipes" array
                    JSONObject contentObject = new JSONObject(recipesJson);
                    if (!contentObject.has("recipes")) {
                        logger.error("Response does not contain 'recipes' array. Response: {}", recipesJson);
                        throw new AIServiceException("Invalid response format: missing 'recipes' array");
                    }
                    JSONArray recipesArray = contentObject.getJSONArray("recipes");

                    List<Recipe> recipes = new ArrayList<>();
                    // ... (rest of your existing parsing and saving logic is below) ...

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
                            logger.info("Found similar recipe '{}' for '{}'. Using existing recipe from database.", 
                                       similarRecipeOpt.get().getName(), recipeName);
                            recipe = similarRecipeOpt.get();
                        } else {
                            // Check by ingredientKey as fallback
                            String ingredientKey = generateIngredientKey(recipeName);

                            if (ingredientKey == null || ingredientKey.isBlank()) {
                                logger.warn("Skipping recipe due to missing ingredientKey");
                                continue;
                            }

                            Optional<Recipe> existingRecipeOpt = recipeRepo.findByIngredientKey(ingredientKey);
                            if (existingRecipeOpt.isPresent()) {
                                logger.info("Recipe with ingredientKey '{}' already exists. Using existing recipe.", ingredientKey);
                                recipe = existingRecipeOpt.get();
                            } else {
                                recipe = new Recipe();
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

                                // Save new recipe to DB
                                recipe = recipeRepo.createRecipe(recipe);
                                logger.info("Created new recipe '{}' in database", recipeName);
                            }
                        }
                        recipes.add(recipe);
                    }

                    return recipeMapper.toRecipeReadDtoList(recipes);

                } catch (JSONException e) {
                    logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                    throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                }
            }
        } catch (IOException | JSONException e) {
            logger.error("Error fetching recipes from OpenAI API", e);
            throw new AIServiceException("Error fetching recipes from OpenAI API");
        }
    }

    @Transactional
    public void addImageToRecipe(RecipeReadDto dto) {
        if (dto == null || dto.getId() == null) {
            logger.warn("Cannot add image to null recipe or recipe with null ID");
            return;
        }

        // Skip if recipe already has an image
        if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) {
            logger.debug("Recipe {} already has an image URL, skipping image generation", dto.getName());
            return;
        }

        logger.info("Generating image for recipe: {}", dto.getName());
        try {
            String prompt = dto.getName();
            List<String> ingredientNames = dto.getIngredients() != null ?
                    dto.getIngredients().stream().map(i -> i.getName()).toList() :
                    List.of();
            String description = dto.getDescription() != null ? dto.getDescription() : "";

            // Generate and upload image
            logger.debug("Calling image generator service for recipe: {}", dto.getName());
            String base64 = imageGeneratorService.generateImage(prompt, ingredientNames, description);
            if (base64 == null || base64.isEmpty()) {
                logger.error("Image generator returned empty base64 for recipe: {}", dto.getName());
                throw new GeminiServiceException("Image generator returned empty base64 for recipe: " + dto.getName());
            }

            logger.debug("Decoding base64 image for recipe: {}", dto.getName());
            byte[] imageBytes = Base64.getDecoder().decode(base64);

            logger.debug("Uploading image to S3 for recipe: {}", dto.getName());
            String imageUrl = s3Service.uploadImage(imageBytes, dto.getName());

            if (imageUrl == null || imageUrl.isEmpty()) {
                logger.error("S3 service returned empty imageUrl for recipe: {}", dto.getName());
                throw new GeminiServiceException("S3 service returned empty imageUrl for recipe: " + dto.getName());
            }

            // Update DTO
            dto.setImageUrl(imageUrl);
            logger.info("Successfully uploaded image for recipe {} to: {}", dto.getName(), imageUrl);

            // Update entity in database
            Optional<Recipe> recipeOpt = recipeRepo.getRecipeById(dto.getId());
            if (recipeOpt.isPresent()) {
                Recipe recipe = recipeOpt.get();
                recipe.setImageUrl(imageUrl);
                // Save the updated recipe back to the database
                recipe = recipeRepo.saveRecipe(recipe);
                logger.info("Successfully updated recipe {} in database with image URL", dto.getId());
            } else {
                logger.error("Recipe not found in database: {}", dto.getId());
                throw new GeminiServiceException("Recipe not found in database: " + dto.getId());
            }
        } catch (GeminiServiceException e) {
            logger.error("GeminiServiceException while adding image to recipe {}: {}", dto.getName(), e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid base64 data for recipe {}: {}", dto.getName(), e.getMessage(), e);
            throw new GeminiServiceException("Invalid image data for recipe: " + dto.getName(), e);
        } catch (Exception e) {
            logger.error("Unexpected error generating/uploading image for recipe {}: {}", dto.getName(), e.getMessage(), e);
            throw new GeminiServiceException("Failed to generate/upload image for recipe: " + dto.getName(), e);
        }
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
     */
    private Optional<Recipe> findSimilarRecipe(String recipeName) {
        if (recipeName == null || recipeName.trim().isEmpty()) {
            return Optional.empty();
        }
        
        List<Recipe> allRecipes = recipeRepo.findAllRecipes();
        if (allRecipes.isEmpty()) {
            return Optional.empty();
        }
        
        double maxSimilarity = 0.0;
        Recipe mostSimilarRecipe = null;
        
        for (Recipe recipe : allRecipes) {
            if (recipe.getName() == null) {
                continue;
            }
            
            double similarity = calculateSimilarity(recipeName, recipe.getName());
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                mostSimilarRecipe = recipe;
            }
        }
        
        // Return recipe if similarity is >= 90% (0.9)
        if (maxSimilarity >= 0.9 && mostSimilarRecipe != null) {
            logger.info("Found similar recipe '{}' with {:.2f}% similarity to '{}'", 
                       mostSimilarRecipe.getName(), maxSimilarity * 100, recipeName);
            return Optional.of(mostSimilarRecipe);
        }
        
        return Optional.empty();
    }
}