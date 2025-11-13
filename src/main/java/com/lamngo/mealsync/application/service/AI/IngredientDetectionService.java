package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.presentation.error.AIServiceException;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for detecting raw ingredients from images using OpenAI Vision API.
 * This service is separate from recipe generation to maintain single responsibility.
 */
@Service
public class IngredientDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(IngredientDetectionService.class);

    @Value("${OPENAI_API_BASE_URL}")
    private String openAIApiBaseUrl;

    @Value("${OPENAI_API_KEY}")
    private String openAIApiKey;

    private static final String GPT_MODEL = "gpt-4o-mini";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    
    private final PromptLoader promptLoader;
    
    public IngredientDetectionService(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    /**
     * Sanitizes a validated ingredient from AI response (basic null checks and trimming).
     * Validation is already done by the AI prompt, so this only handles basic sanitization.
     */
    private DetectedIngredientDto sanitizeIngredient(JSONObject ingObj) {
        if (ingObj == null) {
            logger.warn("Ingredient object is null");
            return null;
        }
        
        String name = ingObj.optString("name", "").trim();
        String quantity = ingObj.optString("quantity", "").trim();
        String unit = ingObj.optString("unit", "").trim();
        
        // Basic null/empty checks - if name is missing, skip it
        if (name == null || name.isEmpty()) {
            logger.warn("Skipping ingredient with missing name");
            return null;
        }
        
        // If quantity is missing, set default (though prompt should handle this)
        if (quantity == null || quantity.isEmpty()) {
            quantity = "1";
        }
        
        // Unit can be empty, that's fine
        if (unit == null) {
            unit = "";
        }
        
        DetectedIngredientDto dto = new DetectedIngredientDto();
        dto.setName(name);
        dto.setQuantity(quantity);
        dto.setUnit(unit);
        return dto;
    }

    /**
     * Validates ingredients provided by the user using OpenAI API.
     * This method validates ingredient names, quantities, and units,
     * and returns only valid ingredients.
     *
     * @param ingredients The list of ingredients to validate (with name, quantity, and unit)
     * @return List of validated ingredients with name, quantity, and unit
     * @throws AIServiceException if the validation fails
     */
    public List<DetectedIngredientDto> validateAndParseIngredientsFromText(List<DetectedIngredientDto> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            logger.warn("Ingredients list is empty or null for validation");
            throw new AIServiceException("Ingredients list is empty or null");
        }

        logger.info("Validating {} ingredients provided by user", ingredients.size());
        try {
            if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                    || openAIApiKey == null || openAIApiKey.isEmpty()) {
                logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly");
                throw new AIServiceException("OpenAI API configuration error");
            }

            // Convert ingredients list to JSON for the prompt
            JSONArray ingredientsJsonArray = new JSONArray();
            for (DetectedIngredientDto ingredient : ingredients) {
                JSONObject ingObj = new JSONObject();
                ingObj.put("name", ingredient.getName() != null ? ingredient.getName() : "");
                ingObj.put("quantity", ingredient.getQuantity() != null ? ingredient.getQuantity() : "");
                ingObj.put("unit", ingredient.getUnit() != null ? ingredient.getUnit() : "");
                ingredientsJsonArray.put(ingObj);
            }
            String ingredientsJsonString = ingredientsJsonArray.toString();

            // Load the ingredient validation prompt
            String prompt = promptLoader.loadPrompt("ingredient-validation.txt");

            // Construct the OpenAI Request Body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GPT_MODEL);
            requestBody.put("response_format", new JSONObject().put("type", "json_object"));

            // Build the messages array with the prompt and ingredients JSON
            requestBody.put("messages", new JSONArray()
                    .put(new JSONObject()
                            .put("role", "system")
                            .put("content", prompt))
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("content", "Please validate these ingredients: " + ingredientsJsonString)));

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(openAIApiBaseUrl)
                    .post(body)
                    .header("Authorization", "Bearer " + openAIApiKey)
                    .build();

            logger.info("Sending text-based ingredient validation and parsing request to OpenAI API using {}", GPT_MODEL);

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
                    String ingredientsJson = message.getString("content").trim();

                    logger.debug("Raw OpenAI ingredient detection response: {}", ingredientsJson);

                    JSONObject contentObject = new JSONObject(ingredientsJson);
                    if (!contentObject.has("ingredients")) {
                        logger.error("Response does not contain 'ingredients' array. Response: {}", ingredientsJson);
                        throw new AIServiceException("Invalid response format: missing 'ingredients' array");
                    }

                    JSONArray ingredientsArray = contentObject.getJSONArray("ingredients");
                    List<DetectedIngredientDto> validatedIngredients = new ArrayList<>();
                    int skippedCount = 0;

                    for (int i = 0; i < ingredientsArray.length(); i++) {
                        try {
                            JSONObject ingObj = ingredientsArray.getJSONObject(i);
                            DetectedIngredientDto ingredient = sanitizeIngredient(ingObj);
                            
                            if (ingredient != null) {
                                validatedIngredients.add(ingredient);
                            } else {
                                skippedCount++;
                            }
                        } catch (JSONException e) {
                            logger.warn("Failed to parse ingredient at index {}: {}", i, e.getMessage());
                            skippedCount++;
                        }
                    }

                    if (skippedCount > 0) {
                        logger.warn("Skipped {} invalid or unclear ingredients during detection", skippedCount);
                    }

                    if (validatedIngredients.isEmpty()) {
                        logger.warn("No valid ingredients detected after validation");
                    }

                    logger.info("Successfully validated {} ingredients (skipped {} invalid)", 
                                validatedIngredients.size(), skippedCount);
                    return validatedIngredients;

                } catch (JSONException e) {
                    logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                    throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                }
            }
        } catch (IOException | JSONException e) {
            logger.error("Error detecting ingredients from OpenAI API", e);
            throw new AIServiceException("Error detecting ingredients from OpenAI API: " + e.getMessage());
        }
    }

    /**
     * Detects raw ingredients from an uploaded image using OpenAI Vision API.
     * Returns a list of ingredients with name, quantity, and unit.
     *
     * @param imageFile The uploaded image file containing ingredients
     * @return List of detected ingredients with name, quantity, and unit
     * @throws AIServiceException if the detection fails
     */
    public List<DetectedIngredientDto> detectRawIngredients(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            logger.warn("Image file is empty or null for ingredient detection");
            throw new AIServiceException("Image file is empty or null");
        }

        logger.info("Detecting raw ingredients from image: {}", imageFile.getOriginalFilename());
        try {
            if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                    || openAIApiKey == null || openAIApiKey.isEmpty()) {
                logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly");
                throw new AIServiceException("OpenAI API configuration error");
            }

            // Prepare Base64 Image Data
            String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
            String mimeType = Optional.ofNullable(imageFile.getContentType())
                    .orElse(MimeTypeUtils.IMAGE_JPEG_VALUE);

            // Prompt for ingredient detection with quantities and units
            // Load the ingredient detection from image prompt
            String prompt = promptLoader.loadPrompt("ingredient-detection-image.txt");

            // Construct the OpenAI Multimodal Request Body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GPT_MODEL);
            requestBody.put("response_format", new JSONObject().put("type", "json_object"));

            // Build the contents array with image and text
            JSONArray contentParts = new JSONArray()
                    .put(new JSONObject().put("type", "text").put("text", prompt))
                    .put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", "data:" + mimeType + ";base64," + base64Image)));

            requestBody.put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "user").put("content", contentParts)));

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(openAIApiBaseUrl)
                    .post(body)
                    .header("Authorization", "Bearer " + openAIApiKey)
                    .build();

            logger.info("Sending ingredient detection request to OpenAI API using {}", GPT_MODEL);

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
                    String ingredientsJson = message.getString("content").trim();

                    logger.debug("Raw OpenAI ingredient detection response: {}", ingredientsJson);

                    JSONObject contentObject = new JSONObject(ingredientsJson);
                    if (!contentObject.has("ingredients")) {
                        logger.error("Response does not contain 'ingredients' array. Response: {}", ingredientsJson);
                        throw new AIServiceException("Invalid response format: missing 'ingredients' array");
                    }

                    JSONArray ingredientsArray = contentObject.getJSONArray("ingredients");
                    List<DetectedIngredientDto> ingredients = new ArrayList<>();
                    int skippedCount = 0;

                    for (int i = 0; i < ingredientsArray.length(); i++) {
                        try {
                            JSONObject ingObj = ingredientsArray.getJSONObject(i);
                            DetectedIngredientDto ingredient = sanitizeIngredient(ingObj);
                            
                            if (ingredient != null) {
                                ingredients.add(ingredient);
                            } else {
                                skippedCount++;
                            }
                        } catch (JSONException e) {
                            logger.warn("Failed to parse ingredient at index {}: {}", i, e.getMessage());
                            skippedCount++;
                        }
                    }

                    if (skippedCount > 0) {
                        logger.warn("Skipped {} invalid or unclear ingredients during detection", skippedCount);
                    }

                    if (ingredients.isEmpty()) {
                        logger.warn("No valid ingredients detected after validation");
                    }

                    logger.info("Successfully detected {} valid ingredients from image (skipped {} invalid)", 
                                ingredients.size(), skippedCount);
                    return ingredients;

                } catch (JSONException e) {
                    logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                    throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                }
            }
        } catch (IOException | JSONException e) {
            logger.error("Error detecting ingredients from OpenAI API", e);
            throw new AIServiceException("Error detecting ingredients from OpenAI API: " + e.getMessage());
        }
    }
}
