package com.lamngo.mealsync.application.service.AI;

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
     * Detects and validates ingredients from manually entered text using OpenAI API.
     * This method processes user input, identifies valid ingredients, and standardizes their names.
     *
     * @param textInput The user's manual text input describing ingredients
     * @return List of detected and standardized ingredient names
     * @throws AIServiceException if the detection fails
     */
    public List<String> detectIngredientsFromText(String textInput) {
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

            // Prompt for ingredient detection from text
            String prompt =
                    "You are a professional chef and food recognition expert. " +
                            "Analyze the provided text and identify all raw, edible ingredients mentioned. " +
                            "Focus ONLY on food items that can be used as ingredients in cooking. " +
                            "Standardize ingredient names to their common culinary form (e.g., 'tomatos' -> 'tomatoes', '2 eggs' -> 'eggs'). " +
                            "Ignore any quantities, measurements, or non-food items. " +
                            "Return ONLY a JSON object with an 'ingredients' array containing the standardized names of detected ingredients as strings. " +
                            "The structure must be: {\"ingredients\": [\"ingredient1\", \"ingredient2\", ...]}. " +
                            "Do not include any extra text, explanation, or commentary outside the JSON object. " +
                            "If no valid ingredients are detected, return {\"ingredients\": []}.";

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
                    List<String> ingredients = new ArrayList<>();
                    for (int i = 0; i < ingredientsArray.length(); i++) {
                        String ingredient = ingredientsArray.getString(i);
                        if (ingredient != null && !ingredient.trim().isEmpty()) {
                            ingredients.add(ingredient.trim());
                        }
                    }

                    logger.info("Successfully detected {} ingredients from text input", ingredients.size());
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
     * Returns a simple array of ingredient names without generating full recipes.
     *
     * @param imageFile The uploaded image file containing ingredients
     * @return List of detected ingredient names
     * @throws AIServiceException if the detection fails
     */
    public List<String> detectRawIngredients(MultipartFile imageFile) {
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

            // Prompt for ingredient detection only
            String prompt =
                    "You are a professional chef and food recognition expert. " +
                            "Analyze the provided image and identify all raw, edible ingredients visible in the image. " +
                            "Focus ONLY on food items that can be used as ingredients in cooking. " +
                            "Ignore any non-food items, containers, utensils, or packaging. " +
                            "Return ONLY a JSON object with an 'ingredients' array containing the names of detected ingredients as strings. " +
                            "The structure must be: {\"ingredients\": [\"ingredient1\", \"ingredient2\", ...]}. " +
                            "Do not include any extra text, explanation, or commentary outside the JSON object. " +
                            "If no valid ingredients are detected, return {\"ingredients\": []}.";

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
                    List<String> ingredients = new ArrayList<>();
                    for (int i = 0; i < ingredientsArray.length(); i++) {
                        String ingredient = ingredientsArray.getString(i);
                        if (ingredient != null && !ingredient.trim().isEmpty()) {
                            ingredients.add(ingredient.trim());
                        }
                    }

                    logger.info("Successfully detected {} ingredients from image", ingredients.size());
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
