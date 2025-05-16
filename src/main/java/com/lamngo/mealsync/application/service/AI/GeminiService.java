package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeIngredient;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.presentation.error.GeminiServiceException;
import jakarta.transaction.Transactional;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;

import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;

@Service
@Transactional
public class GeminiService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${GEMINI_API_BASE_URL}")
    private String geminiApiBaseUrl;

    @Value("${GEMINI_API_KEY}")
    private String geminiApiKey;

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Autowired
    private RecipeMapper recipeMapper;

    @Autowired
    private IRecipeRepo recipeRepo;

    @Autowired
    private IRecipeIngredient recipeIngredientRepo;

    public static void setApiKey(String key) {
        // apiKey is not used anymore, consider removing this method
    }

    /**
     * Generates a list of recipes using Gemini based on ingredients and user preferences.
     *
     * @param ingredients    List of ingredient names
     * @param userPreference UserPreference object
     * @return Recipes as a list of RecipeReadDto objects
     */

    public List<RecipeReadDto> generateRecipes(List<String> ingredients, UserPreference userPreference) {
        try {
            if (ingredients == null || ingredients.isEmpty()) {
                logger.warn("Ingredient list is empty or null");
                throw new IllegalArgumentException("Ingredient list cannot be empty");
            }
            if (userPreference == null) {
                userPreference = new UserPreference(); // or handle as needed
            }

            if (geminiApiBaseUrl == null || !geminiApiBaseUrl.startsWith("https://")
                    || geminiApiKey == null || geminiApiKey.isEmpty() ) {
                logger.error("GEMINI_API_BASE_URL or GEMINI_API_KEY is not set properly. Check your env.properties file.");
                throw new IllegalStateException("Please set GEMINI_API_BASE_URL and GEMINI_API_KEY in env.properties");
            }

            String prompt = "Given these ingredients: " + String.join(", ", ingredients) +
                    ". And these user preferences: dietary restrictions: " + userPreference.getDietaryRestrictions() +
                    ", favorite cuisines: " + userPreference.getFavoriteCuisines() +
                    ", disliked ingredients: " + userPreference.getDislikedIngredients() + "." +
                    " Generate exactly 2 creative recipes and respond with ONLY a valid JSON array. Do not include any text or explanation. Each recipe should have the following fields: " +
                    "name, instructions (as a string array, one step per item), cuisine, imageUrl, description, preparationTime, cookingTime, totalTime, servings, difficulty, tags, " +
                    "and ingredients (with name, quantity, unit).";

            JSONObject requestBody = new JSONObject();
            requestBody.put("contents", new JSONArray()
                    .put(new JSONObject().put("parts", new JSONArray()
                            .put(new JSONObject().put("text", prompt)))));

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
            );

            String fullUrl = geminiApiBaseUrl + "?key=" + geminiApiKey;
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(body)
                    .build();
            logger.info("Sending request to Gemini API: {}", request);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Gemini API call failed: HTTP {} - {}", response.code(), response.message());
                    throw new IOException("Gemini API call failed: HTTP " + response.code() + " - " + response.message());
                }
                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null) {
                    logger.error("Empty response from Gemini API");
                    throw new IOException("Empty response from Gemini API");
                }
                try {
                    JSONObject json = new JSONObject(responseBody);
                    if (!json.has("candidates") || json.getJSONArray("candidates").isEmpty()) {
                        logger.error("No candidates returned from Gemini API");
                        throw new IOException("No candidates returned from Gemini API");
                    }
                    JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                    if (!candidate.has("content")) {
                        logger.error("Malformed Gemini API response: missing content");
                        throw new IOException("Malformed Gemini API response: missing content");
                    }
                    JSONObject content = candidate.getJSONObject("content");
                    if (!content.has("parts") || content.getJSONArray("parts").isEmpty()) {
                        logger.error("Malformed Gemini API response: missing parts");
                        throw new IOException("Malformed Gemini API response: missing parts");
                    }
                    JSONObject part = content.getJSONArray("parts").getJSONObject(0);
                    if (!part.has("text")) {
                        logger.error("Malformed Gemini API response: missing text");
                        throw new IOException("Malformed Gemini API response: missing text");
                    }
                    String recipesJson = part.getString("text").trim();

                    // Extract the valid JSON array using regex if the response contains extra text
                    Pattern jsonArrayPattern = Pattern.compile("(\\[.*])", Pattern.DOTALL);
                    Matcher matcher = jsonArrayPattern.matcher(recipesJson);
                    if (matcher.find()) {
                        recipesJson = matcher.group(1);

                    } else {
                        throw new IOException("No valid JSON array found in Gemini response");
                    }

                    // Parse JSON array of recipes
                    JSONArray recipesArray = new JSONArray(recipesJson);
                    // printout the JSON array for debugging
                    for (int i = 0; i < recipesArray.length(); i++) {
                        logger.info("Recipe {}: {}", i + 1, recipesArray.getJSONObject(i).toString(2));
                    }
                    List<Recipe> recipes = new ArrayList<>();
                    for (int i = 0; i < recipesArray.length(); i++) {
                        JSONObject obj = recipesArray.getJSONObject(i);
                        String recipeName = obj.optString("name", null);
                        String ingredientKey = generateIngredientKey(recipeName);

                        if (ingredientKey == null || ingredientKey.isBlank()) {
                            logger.warn("Skipping recipe due to missing ingredientKey");
                            continue;
                        }

                        Optional<Recipe> existingRecipeOpt = recipeRepo.findByIngredientKey(ingredientKey);
                        Recipe recipe;
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
                            recipe.setImageUrl(obj.optString("imageUrl", null));
                            recipe.setIngredientKey(ingredientKey);
                            recipe.setDescription(obj.optString("description", ""));
                            recipe.setPreparationTime(obj.optInt("preparationTime", 0));
                            recipe.setCookingTime(obj.optInt("cookingTime", 0));
                            recipe.setTotalTime(obj.optInt("totalTime", 0));
                            recipe.setServings(obj.optInt("servings", 1));
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

                            recipe.setSource(obj.optString("source", "AI"));

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
                        }
                        recipes.add(recipe);
                    }
                    // Convert to List<RecipeReadDto>
                    return recipeMapper.toRecipeReadDtoList(recipes);
                } catch (JSONException e) {
                    logger.error("Failed to parse Gemini API response: {}", e.getMessage());
                    throw new IOException("Failed to parse Gemini API response: " + e.getMessage(), e);
                }
            }
        } catch (IOException | org.json.JSONException e) {
            logger.error("Error generating recipes from Gemini API", e);
            throw new GeminiServiceException("Failed to generate recipes from Gemini API", e);
        }
    }

    private String generateIngredientKey(String recipeName) {
        if (recipeName == null) return null;
        return recipeName.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "_");
    }
}
