package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.presentation.error.AIServiceException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for detecting raw ingredients from images using OpenAI Vision API.
 * This service is separate from recipe generation to maintain single
 * responsibility.
 */
@Service
public class IngredientDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(IngredientDetectionService.class);

    @Value("${OPENAI_API_BASE_URL}")
    private String openAIApiBaseUrl;

    @Value("${OPENAI_API_KEY}")
    private String openAIApiKey;

    // NOTE: Using gpt-4o-mini for cost efficiency and speed. For better
    // instruction-following and validation accuracy,
    // consider upgrading to "gpt-4o" or "gpt-4-turbo" if validation quality issues
    // persist.
    private static final String GPT_MODEL = "gpt-4o-mini";

    private WebClient openAIWebClient;

    private final PromptLoader promptLoader;

    // Cache prompts to avoid repeated file I/O
    // Note: Cache is cleared on application restart, so prompt changes require a
    // restart
    private volatile String cachedValidationPrompt = null;
    private volatile String cachedImageDetectionPrompt = null;

    public IngredientDetectionService(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    @PostConstruct
    public void init() {
        // Initialize WebClient for async OpenAI API calls
        this.openAIWebClient = WebClient.builder()
                .baseUrl(openAIApiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + openAIApiKey)
                .build();
    }

    /**
     * Gets the validation prompt, loading from cache if available.
     * Cache is cleared on application restart.
     */
    private String getValidationPrompt() {
        if (cachedValidationPrompt == null) {
            synchronized (this) {
                if (cachedValidationPrompt == null) {
                    cachedValidationPrompt = promptLoader.loadPrompt("ingredient-validation.txt");
                    logger.debug("Loaded and cached ingredient validation prompt");
                }
            }
        }
        return cachedValidationPrompt;
    }

    /**
     * Gets the image detection prompt, loading from cache if available.
     * Cache is cleared on application restart.
     */
    private String getImageDetectionPrompt() {
        if (cachedImageDetectionPrompt == null) {
            synchronized (this) {
                if (cachedImageDetectionPrompt == null) {
                    cachedImageDetectionPrompt = promptLoader.loadPrompt("ingredient-detection-image.txt");
                    logger.debug("Loaded and cached image detection prompt");
                }
            }
        }
        return cachedImageDetectionPrompt;
    }

    /**
     * Sanitizes a validated ingredient from AI response (basic null checks and
     * trimming).
     * Validation is already done by the AI prompt, so this only handles basic
     * sanitization.
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

        // Rely on AI prompt validation - the prompt is explicit and temperature=0
        // ensures consistency
        // Programmatic validation removed to avoid false positives (e.g., "chicken
        // wings")

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
     * @param ingredients The list of ingredients to validate (with name, quantity,
     *                    and unit)
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

            // Use async OpenAI call but block to get results immediately
            // This is still faster than blocking OkHttpClient because WebClient is more
            // efficient
            List<DetectedIngredientDto> validatedIngredients = validateIngredientsFromTextAsync(ingredients).join();

            logger.info("Successfully validated {} ingredients", validatedIngredients.size());
            return validatedIngredients;
        } catch (AIServiceException e) {
            logger.error("AI service error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error validating ingredients: {}", e.getMessage(), e);
            throw new AIServiceException("Failed to validate ingredients: " + e.getMessage());
        }
    }

    /**
     * Async version of validateAndParseIngredientsFromText using WebClient
     */
    private CompletableFuture<List<DetectedIngredientDto>> validateIngredientsFromTextAsync(
            List<DetectedIngredientDto> ingredients) {
        if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                || openAIApiKey == null || openAIApiKey.isEmpty()) {
            logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly");
            return CompletableFuture.failedFuture(new AIServiceException("OpenAI API configuration error"));
        }

        // Convert ingredients list to JSON for the prompt (optimized)
        JSONArray ingredientsJsonArray = new JSONArray(ingredients.size());
        for (DetectedIngredientDto ingredient : ingredients) {
            JSONObject ingObj = new JSONObject();
            String name = ingredient.getName();
            String quantity = ingredient.getQuantity();
            String unit = ingredient.getUnit();
            ingObj.put("name", name != null ? name : "");
            ingObj.put("quantity", quantity != null ? quantity : "");
            ingObj.put("unit", unit != null ? unit : "");
            ingredientsJsonArray.put(ingObj);
        }
        String ingredientsJsonString = ingredientsJsonArray.toString();

        // Get cached validation prompt and combine with ingredients
        String prompt = getValidationPrompt();
        String fullPrompt = prompt + "\n\nPlease validate these ingredients: " + ingredientsJsonString;

        logger.debug("Sending validation request with {} ingredients", ingredients.size());

        // Construct the OpenAI Request Body (text-only, no image)
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", GPT_MODEL);
        requestBody.put("temperature", 0);
        requestBody.put("response_format", new JSONObject().put("type", "json_object"));
        requestBody.put("messages", new JSONArray()
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", fullPrompt)));

        return openAIWebClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> {
                            return response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        logger.error("OpenAI API call failed: HTTP {} - Response: {}",
                                                response.statusCode(), errorBody);
                                        String errorMessage = "OpenAI API call failed: HTTP " + response.statusCode();
                                        try {
                                            JSONObject errorJson = new JSONObject(errorBody);
                                            if (errorJson.has("error")
                                                    && errorJson.getJSONObject("error").has("message")) {
                                                errorMessage += " - "
                                                        + errorJson.getJSONObject("error").getString("message");
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
                        String ingredientsJson = message.getString("content").trim();

                        JSONObject contentObject = new JSONObject(ingredientsJson);
                        if (!contentObject.has("ingredients")) {
                            logger.error("Response does not contain 'ingredients' array");
                            throw new AIServiceException("Invalid response format: missing 'ingredients' array");
                        }

                        JSONArray ingredientsArray = contentObject.getJSONArray("ingredients");
                        List<DetectedIngredientDto> validatedIngredients = new ArrayList<>(ingredientsArray.length());
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
                            logger.debug("Skipped {} invalid ingredients during validation", skippedCount);
                        }

                        return validatedIngredients;
                    } catch (JSONException e) {
                        logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                        throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                    }
                })
                .doOnError(error -> {
                    if (!(error instanceof AIServiceException)) {
                        logger.error("Error validating ingredients from OpenAI API", error);
                    }
                })
                .onErrorResume(throwable -> {
                    AIServiceException exception;
                    if (throwable instanceof AIServiceException) {
                        exception = (AIServiceException) throwable;
                    } else {
                        String errorMsg = throwable != null ? throwable.getMessage() : "Unknown error";
                        exception = new AIServiceException("Error validating ingredients from OpenAI API: " + errorMsg);
                    }
                    return Mono.error(exception);
                })
                .toFuture();
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

            // Use async OpenAI call but block to get results immediately
            // This is still faster than blocking OkHttpClient because WebClient is more
            // efficient
            List<DetectedIngredientDto> ingredients = detectRawIngredientsAsync(imageFile).join();

            logger.info("Successfully detected {} ingredients from image", ingredients.size());
            return ingredients;
        } catch (AIServiceException e) {
            logger.error("AI service error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error detecting ingredients: {}", e.getMessage(), e);
            throw new AIServiceException("Failed to detect ingredients: " + e.getMessage());
        }
    }

    /**
     * Async version of detectRawIngredients using WebClient
     */
    public CompletableFuture<List<DetectedIngredientDto>> detectRawIngredientsAsync(MultipartFile imageFile) {
        if (openAIApiBaseUrl == null || !openAIApiBaseUrl.startsWith("https://")
                || openAIApiKey == null || openAIApiKey.isEmpty()) {
            logger.error("OPENAI_API_BASE_URL or OPENAI_API_KEY is not set properly");
            return CompletableFuture.failedFuture(new AIServiceException("OpenAI API configuration error"));
        }

        try {
            // Prepare Base64 Image Data
            byte[] imageBytes = imageFile.getBytes();

            // Resize image if too large (max 1024x1024) to reduce latency
            // This is a critical optimization for Vision API speed
            try {
                imageBytes = resizeImageIfNeeded(imageBytes, 1024);
                logger.debug("Image resized/optimized. Size: {} bytes", imageBytes.length);
            } catch (Exception e) {
                logger.warn("Failed to resize image, using original: {}", e.getMessage());
                // Fallback to original bytes
            }

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = Optional.ofNullable(imageFile.getContentType())
                    .orElse(MimeTypeUtils.IMAGE_JPEG_VALUE);

            // Get cached image detection prompt
            String prompt = getImageDetectionPrompt();

            // Construct the OpenAI Multimodal Request Body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GPT_MODEL);
            requestBody.put("temperature", 0);
            requestBody.put("response_format", new JSONObject().put("type", "json_object"));

            // Build the contents array with image and text
            JSONArray contentParts = new JSONArray()
                    .put(new JSONObject().put("type", "text").put("text", prompt))
                    .put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", "data:" + mimeType + ";base64," + base64Image)));

            requestBody.put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "user").put("content", contentParts)));

            logger.debug("Sending ingredient detection request to OpenAI API using {}", GPT_MODEL);

            return openAIWebClient.post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> {
                                return response.bodyToMono(String.class)
                                        .flatMap(errorBody -> {
                                            logger.error("OpenAI API call failed: HTTP {} - Response: {}",
                                                    response.statusCode(), errorBody);
                                            String errorMessage = "OpenAI API call failed: HTTP "
                                                    + response.statusCode();
                                            try {
                                                JSONObject errorJson = new JSONObject(errorBody);
                                                if (errorJson.has("error")
                                                        && errorJson.getJSONObject("error").has("message")) {
                                                    errorMessage += " - "
                                                            + errorJson.getJSONObject("error").getString("message");
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
                            String ingredientsJson = message.getString("content").trim();

                            JSONObject contentObject = new JSONObject(ingredientsJson);
                            if (!contentObject.has("ingredients")) {
                                logger.error("Response does not contain 'ingredients' array");
                                throw new AIServiceException("Invalid response format: missing 'ingredients' array");
                            }

                            JSONArray ingredientsArray = contentObject.getJSONArray("ingredients");
                            List<DetectedIngredientDto> ingredients = new ArrayList<>(ingredientsArray.length());
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
                                logger.debug("Skipped {} invalid ingredients during detection", skippedCount);
                            }

                            return ingredients;
                        } catch (JSONException e) {
                            logger.error("Failed to parse OpenAI API response: {}", e.getMessage(), e);
                            throw new AIServiceException("Failed to parse OpenAI API response: " + e.getMessage());
                        }
                    })
                    .doOnError(error -> {
                        if (!(error instanceof AIServiceException)) {
                            logger.error("Error detecting ingredients from OpenAI API", error);
                        }
                    })
                    .onErrorResume(throwable -> {
                        AIServiceException exception;
                        if (throwable instanceof AIServiceException) {
                            exception = (AIServiceException) throwable;
                        } else {
                            String errorMsg = throwable != null ? throwable.getMessage() : "Unknown error";
                            exception = new AIServiceException(
                                    "Error detecting ingredients from OpenAI API: " + errorMsg);
                        }
                        return Mono.error(exception);
                    })
                    .toFuture();
        } catch (IOException e) {
            logger.error("Error reading image file: {}", e.getMessage(), e);
            return CompletableFuture
                    .failedFuture(new AIServiceException("Error reading image file: " + e.getMessage()));
        }
    }

    /**
     * Resizes an image byte array if it exceeds the specified maximum dimension.
     * Maintains aspect ratio.
     */
    private byte[] resizeImageIfNeeded(byte[] originalImageBytes, int maxDimension) throws IOException {
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(originalImageBytes);
        java.awt.image.BufferedImage originalImage = javax.imageio.ImageIO.read(bais);

        if (originalImage == null) {
            return originalImageBytes; // Not an image or unsupported format
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // If image is already smaller than max dimension, return original
        if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
            return originalImageBytes;
        }

        // Calculate new dimensions maintaining aspect ratio
        int newWidth;
        int newHeight;

        if (originalWidth > originalHeight) {
            newWidth = maxDimension;
            newHeight = (int) (originalHeight * ((double) maxDimension / originalWidth));
        } else {
            newHeight = maxDimension;
            newWidth = (int) (originalWidth * ((double) maxDimension / originalHeight));
        }

        // Create resized image
        java.awt.image.BufferedImage resizedImage = new java.awt.image.BufferedImage(
                newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resizedImage.createGraphics();

        // Use high quality rendering hints
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // Write to byte array
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(resizedImage, "jpg", baos); // Convert to JPG for consistency/compression
        return baos.toByteArray();
    }
}
