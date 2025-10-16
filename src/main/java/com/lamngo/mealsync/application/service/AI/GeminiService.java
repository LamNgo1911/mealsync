package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.service.AWS.S3Service;
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
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Base64;
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

    private final RecipeMapper recipeMapper;

    private final IRecipeRepo recipeRepo;

    private final ImageGeneratorService imageGeneratorService;

    private final S3Service s3Service;

    public GeminiService(
             RecipeMapper recipeMapper,
             IRecipeRepo recipeRepo,
             IRecipeIngredient recipeIngredientRepo,
             ImageGeneratorService imageGeneratorService,
             S3Service s3Service) {
        this.recipeMapper = recipeMapper;
        this.recipeRepo = recipeRepo;
        this.imageGeneratorService = imageGeneratorService;
        this.s3Service = s3Service;
    }

    @Transactional
    public List<RecipeReadDto> generateRecipes(List<String> ingredients, UserPreference userPreference) {
        if (CollectionUtils.isEmpty(ingredients)) {
            logger.warn("Ingredient list is empty or null");
            throw new GeminiServiceException("Ingredient list is empty or null");
        }
        if (userPreference == null) {
            userPreference = new UserPreference();
            logger.debug("No user preference provided, using default");
        }
        
        logger.info("Generating recipes for ingredients: {}", ingredients);
        try {
            List<RecipeReadDto> recipeDtos = fetchRecipesFromGemini(ingredients, userPreference);
            logger.info("Successfully fetched {} recipes from Gemini", recipeDtos.size());
            
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
        } catch (GeminiServiceException e) {
            logger.error("Gemini service error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error generating recipes: {}", e.getMessage(), e);
            throw new GeminiServiceException("Failed to generate recipes: " + e.getMessage(), e);
        }
    }

    public List<RecipeReadDto> fetchRecipesFromGemini(List<String> ingredients, UserPreference userPreference) {
        try {
            if (geminiApiBaseUrl == null || !geminiApiBaseUrl.startsWith("https://")
                    || geminiApiKey == null || geminiApiKey.isEmpty() ) {
                logger.error("GEMINI_API_BASE_URL or GEMINI_API_KEY is not set properly. Check your env.properties file.");
                throw new GeminiServiceException("GEMINI_API_BASE_URL or GEMINI_API_KEY is not set properly. Check your env.properties file.");
            }

            String prompt = "You are a professional recipe creator. " +
                    "Given the following ingredients: " + String.join(", ", ingredients) + ". " +
                    "And the following user preferences: " +
                    "dietary restrictions: " + userPreference.getDietaryRestrictions() + ", " +
                    "favorite cuisines: " + userPreference.getFavoriteCuisines() + ", " +
                    "disliked ingredients: " + userPreference.getDislikedIngredients() + ". " +
                    "Generate exactly 2 unique and creative recipes that fully comply with these preferences. " +
                    "Respond ONLY with a valid JSON array containing exactly 2 objects. " +
                    "Do NOT include any extra text, explanations, or formatting. " +
                    "Each recipe object must follow this exact structure:\n" +
                    "{\n" +
                    "  \"name\": \"string\",\n" +
                    "  \"instructions\": [\"string\", \"string\", ...],\n" +
                    "  \"cuisine\": \"string\",\n" +
                    "  \"description\": \"string\",\n" +
                    "  \"preparationTime\": integer (minutes),\n" +
                    "  \"cookingTime\": integer (minutes),\n" +
                    "  \"totalTime\": integer (minutes),\n" +
                    "  \"servings\": integer,\n" +
                    "  \"calories\": number (kcal per serving),\n" +
                    "  \"protein\": number (grams per serving),\n" +
                    "  \"carbohydrates\": number (grams per serving),\n" +
                    "  \"fat\": number (grams per serving),\n" +
                    "  \"difficulty\": \"string\" (one of \"easy\", \"medium\", \"hard\"),\n" +
                    "  \"tags\": [\"string\", \"string\", ...],\n" +
                    "  \"ingredients\": [\n" +
                    "    {\n" +
                    "      \"name\": \"string\",\n" +
                    "      \"quantity\": number,\n" +
                    "      \"unit\": \"string\"\n" +
                    "    }, ...\n" +
                    "  ]\n" +
                    "} \n" +
                    "Ensure all numeric values are realistic and consistent. " +
                    "Do not include any additional fields, text, or explanation outside this JSON structure.";


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
                    throw new GeminiServiceException("Gemini API call failed: HTTP " + response.code() + " - " + response.message());
                }
                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null) {
                    logger.error("Empty response from Gemini API");
                    throw new GeminiServiceException("Empty response from Gemini API");
                }
                try {
                    JSONObject json = new JSONObject(responseBody);
                    if (!json.has("candidates") || json.getJSONArray("candidates").isEmpty()) {
                        logger.error("No candidates returned from Gemini API");
                        throw new GeminiServiceException("No candidates returned from Gemini API");
                    }
                    JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                    if (!candidate.has("content")) {
                        logger.error("Malformed Gemini API response: missing content");
                        throw new GeminiServiceException("Malformed Gemini API response: missing content");
                    }
                    JSONObject content = candidate.getJSONObject("content");
                    if (!content.has("parts") || content.getJSONArray("parts").isEmpty()) {
                        logger.error("Malformed Gemini API response: missing parts");
                        throw new GeminiServiceException("Malformed Gemini API response: missing parts");
                    }
                    JSONObject part = content.getJSONArray("parts").getJSONObject(0);
                    if (!part.has("text")) {
                        logger.error("Malformed Gemini API response: missing text");
                        throw new GeminiServiceException("Malformed Gemini API response: missing text");
                    }
                    String recipesJson = part.getString("text").trim();

                    // Extract JSON array from the response
                    Pattern jsonArrayPattern = Pattern.compile("(\\[.*])", Pattern.DOTALL);
                    Matcher matcher = jsonArrayPattern.matcher(recipesJson);
                    if (matcher.find()) {
                        recipesJson = matcher.group(1);

                    } else {
                        throw new GeminiServiceException("No valid JSON array found in Gemini response");
                    }

                    // Parse JSON array of recipes
                    JSONArray recipesArray = new JSONArray(recipesJson);

                    List<Recipe> recipes = new ArrayList<>();
                    for (int i = 0; i < recipesArray.length(); i++) {
                        JSONObject obj = recipesArray.getJSONObject(i);
                        String recipeName = obj.optString("name", null);
                        String description = obj.optString("description", null);
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
                        }
                        recipes.add(recipe);
                    }
                    // Convert to List<RecipeReadDto>
                    return recipeMapper.toRecipeReadDtoList(recipes);
                } catch (JSONException e) {
                    logger.error("Failed to parse Gemini API response: {}", e.getMessage(), e);
                    throw new GeminiServiceException("Failed to parse Gemini API response: " + e.getMessage(), e);
                }
            }
        } catch (IOException | org.json.JSONException e) {
            logger.error("Error fetching recipes from Gemini API", e);
            throw new GeminiServiceException("Error fetching recipes from Gemini API", e);
        }
    }

    /**
     * Adds imageUrl to the given RecipeReadDto using AI and S3 services.
     * Also updates the recipe in the database with the new image URL.
     * 
     * @param dto The recipe DTO to add image to
     * @throws GeminiServiceException if image generation or upload fails
     */
    @Transactional
    public void addImageToRecipe(RecipeReadDto dto) {
        if (dto == null || dto.getId() == null) {
            logger.warn("Cannot add image to null recipe or recipe with null ID");
            return;
        }
        
        logger.debug("Generating image for recipe: {}", dto.getName());
        try {
            String prompt = dto.getName();
            List<String> ingredientNames = dto.getIngredients() != null ? 
                dto.getIngredients().stream().map(i -> i.getName()).toList() : 
                List.of();
            String description = dto.getDescription() != null ? dto.getDescription() : "";
            
            // Generate and upload image
            String base64 = imageGeneratorService.generateImage(prompt, ingredientNames, description);
            if (base64 == null || base64.isEmpty()) {
                logger.warn("Image generator returned empty base64 for recipe: {}", dto.getName());
                throw new GeminiServiceException("Image generator returned empty base64 for recipe: " + dto.getName());
            }
            
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            String imageUrl = s3Service.uploadImage(imageBytes, dto.getName());
            
            // Update DTO
            dto.setImageUrl(imageUrl);
            logger.debug("Successfully uploaded image for recipe {} to: {}", dto.getName(), imageUrl);
            
            // Update entity in database
            Optional<Recipe> recipeOpt = recipeRepo.getRecipeById(dto.getId());
            if (recipeOpt.isPresent()) {
                Recipe recipe = recipeOpt.get();
                recipe.setImageUrl(imageUrl);
                // Save the updated recipe back to the database
                recipe = recipeRepo.saveRecipe(recipe);
                logger.debug("Successfully updated recipe {} with image URL: {}", dto.getId(), recipe.getImageUrl());
            } else {
                logger.warn("Recipe not found in database: {}", dto.getId());
            }
        } catch (GeminiServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to generate/upload image for recipe {}: {}", dto.getName(), e.getMessage(), e);
            throw new GeminiServiceException("Failed to generate/upload image for recipe: " + dto.getName(), e);
        }
    }

    public String generateIngredientKey(String recipeName) {
        if (recipeName == null) return null;
        return recipeName.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "_");
    }
}
