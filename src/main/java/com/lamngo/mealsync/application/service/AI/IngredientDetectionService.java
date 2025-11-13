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

    /**
     * Validates and sanitizes a detected ingredient, applying defaults for missing data
     */
    private DetectedIngredientDto validateAndSanitizeIngredient(JSONObject ingObj) {
        if (ingObj == null) {
            logger.warn("Ingredient object is null");
            return null;
        }
        
        String name = ingObj.optString("name", "").trim();
        
        // If name is empty or too vague, skip it
        if (name == null || name.isEmpty() || name.length() < 2) {
            logger.warn("Skipping ingredient with invalid name: {}", name);
            return null;
        }
        
        // Check for obviously invalid names
        String lowerName = name.toLowerCase();
        if (lowerName.equals("unknown") || lowerName.equals("unclear") || 
            lowerName.equals("?") || lowerName.startsWith("unidentified")) {
            logger.warn("Skipping ingredient with unclear name: {}", name);
            return null;
        }
        
        // Get quantity with default
        String quantity = ingObj.optString("quantity", "").trim();
        if (quantity == null || quantity.isEmpty() || quantity.equals("?") || quantity.equals("unknown")) {
            quantity = "1";  // Default to 1 if unclear
            logger.debug("Using default quantity=1 for ingredient: {}", name);
        }
        
        // Get unit with default
        String unit = ingObj.optString("unit", "").trim();
        if (unit == null) {
            unit = "";
        }
        // Unit can be empty, that's fine
        
        DetectedIngredientDto dto = new DetectedIngredientDto();
        dto.setName(name);
        dto.setQuantity(quantity);
        dto.setUnit(unit);
        return dto;
    }

    /**
     * Detects and validates ingredients from manually entered text using OpenAI API.
     * This method processes user input, identifies valid ingredients, and extracts quantities and units.
     *
     * @param textInput The user's manual text input describing ingredients
     * @return List of detected ingredients with name, quantity, and unit
     * @throws AIServiceException if the detection fails
     */
    public List<DetectedIngredientDto> detectIngredientsFromText(String textInput) {
        if (textInput == null || textInput.trim().isEmpty()) {
            logger.warn("Text input is empty or null for ingredient detection");
            throw new AIServiceException("Text input is empty or null");
        }

        logger.info("Detecting ingredients from text input: {}", textInput);
        try {
            if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                    || openAIApiKey == null || openAIApiKey.isEmpty()) {
                logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly");
                throw new AIServiceException("OpenAI API configuration error");
            }

            // Prompt for ingredient detection from text with quantities and units
            String prompt =
                    "You are a professional chef and food recognition expert. " +
                            "Analyze the provided text and identify all raw, edible ingredients mentioned. " +
                            "Focus ONLY on food items that can be used as ingredients in cooking. " +
                            "IMPORTANT: Extract quantities, units, and preparation details when available. " +
                            "If a quantity is unclear or cannot be determined, use quantity='1' and unit=''. " +
                            "If an ingredient name is ambiguous or unclear, use your best judgment but keep it as a valid ingredient name. " +
                            "For example: " +
                            "- '200grams cut beef' → name='beef', quantity='200', unit='grams' " +
                            "- 'some tomatoes' → name='tomatoes', quantity='1', unit='' " +
                            "- 'a bit of salt' → name='salt', quantity='1', unit='pinch' " +
                            "- 'beef' (unclear quantity) → name='beef', quantity='1', unit='' " +
                            "- '2 large tomatoes' → name='tomatoes', quantity='2', unit='pieces' " +
                            "- 'diced chicken breast' → name='chicken breast', quantity='1', unit='piece' " +
                            "Standardize ingredient names to their common culinary form (e.g., 'tomatos' -> 'tomatoes'). " +
                            "Return ONLY a JSON object with an 'ingredients' array containing ingredient objects. " +
                            "The structure must be: {\"ingredients\": [{\"name\": \"ingredient1\", \"quantity\": \"qty\", \"unit\": \"unit1\"}, ...]}. " +
                            "If no valid ingredients are detected, return {\"ingredients\": []}. " +
                            "Do not include any extra text, explanation, or commentary outside the JSON object.";

            // Construct the OpenAI Request Body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GPT_MODEL);
            requestBody.put("response_format", new JSONObject().put("type", "json_object"));

            // Build the messages array with the prompt and user input
            requestBody.put("messages", new JSONArray()
                    .put(new JSONObject()
                            .put("role", "system")
                            .put("content", prompt))
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("content", textInput)));

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(openAIApiBaseUrl)
                    .post(body)
                    .header("Authorization", "Bearer " + openAIApiKey)
                    .build();

            logger.info("Sending text-based ingredient detection request to OpenAI API using {}", GPT_MODEL);

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
                            DetectedIngredientDto ingredient = validateAndSanitizeIngredient(ingObj);
                            
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

                    logger.info("Successfully detected {} valid ingredients from text input (skipped {} invalid)", 
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
            String prompt =
                    "You are a professional chef and food recognition expert. " +
                            "Analyze the provided image and identify all raw, edible ingredients visible in the image. " +
                            "Focus ONLY on food items that can be used as ingredients in cooking. " +
                            "If you can estimate quantities or see preparation details (e.g., diced, sliced, cut), include them. " +
                            "If quantity cannot be determined, use quantity='1' and unit=''. " +
                            "For example: " +
                            "- If you see a clear amount like '200g of beef', use name='beef', quantity='200', unit='grams' " +
                            "- If you see 'some tomatoes', use name='tomatoes', quantity='1', unit='' " +
                            "- If you see diced vegetables, use name='vegetables', quantity='1', unit='' " +
                            "Ignore any non-food items, containers, utensils, or packaging. " +
                            "Return ONLY a JSON object with an 'ingredients' array containing ingredient objects. " +
                            "The structure must be: {\"ingredients\": [{\"name\": \"ingredient1\", \"quantity\": \"qty\", \"unit\": \"unit1\"}, ...]}. " +
                            "If no valid ingredients are detected, return {\"ingredients\": []}. " +
                            "Do not include any extra text, explanation, or commentary outside the JSON object.";

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
                            DetectedIngredientDto ingredient = validateAndSanitizeIngredient(ingObj);
                            
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
