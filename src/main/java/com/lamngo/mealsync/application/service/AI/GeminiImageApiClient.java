package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.ImageGeneratorServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level API client for Gemini Image Generation API.
 * Handles direct communication with Gemini API and returns base64-encoded images.
 */
@Service
@Slf4j
public class GeminiImageApiClient {

    private static final String ERROR_MSG_TEMPLATE = "Gemini 2.5 Flash Image API returned error %s. Endpoint: %s. " +
            "Please verify the API key and model name are correct.";
    private static final String GEMINI_MODEL = "gemini-2.5-flash-image";

    private OkHttpClient httpClient;
    private String baseUrl;
    private String apiKey;

    @Value("${GEMINI_API_KEY}")
    private String geminiApiKey;

    @Value("${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com}")
    private String geminiApiBaseUrl;
    
    private final PromptLoader promptLoader;
    
    public GeminiImageApiClient(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    @PostConstruct
    public void init() {
        this.baseUrl = geminiApiBaseUrl;
        this.apiKey = geminiApiKey;
        
        // Use OkHttp with async API for non-blocking I/O
        // This is more efficient than blocking calls, especially for parallel requests
        this.httpClient = new OkHttpClient.Builder()
                .build();
    }

    /**
     * Generate a single image using Gemini 2.5 Flash Image API
     */
    public String callGeminiAPI(String prompt) {
        try {
            return callGeminiAPISingle(prompt);
        } catch (ImageGeneratorServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Image generation failed: {}", e.getMessage(), e);
            throw new ImageGeneratorServiceException("Image generation failed", e);
        }
    }
    
    /**
     * Generate multiple images in parallel using Gemini 2.5 Flash Image API
     * Note: Gemini API doesn't support true batch processing, so we make parallel individual calls
     * Uses OkHttp async API for non-blocking I/O, which is more efficient than blocking calls
     * @param prompts List of prompts to generate images for
     * @return Map of prompt to base64 image string
     */
    public Map<String, String> callGeminiAPIBatch(List<String> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            throw new ImageGeneratorServiceException("Prompts list is empty or null");
        }

        try {
            // Use OkHttp async API for non-blocking I/O
            // This is more efficient than blocking calls with CompletableFuture.supplyAsync()
            Map<String, String> results = new HashMap<>();
            
            List<CompletableFuture<Map.Entry<String, String>>> futures = prompts.stream()
                    .map(prompt -> callGeminiAPISingleAsync(prompt)
                            .thenApply(base64Image -> {
                                if (base64Image != null) {
                                    return Map.entry(prompt, base64Image);
                                }
                                return null;
                            })
                            .exceptionally(e -> {
                                log.error("Failed to generate image for prompt: {}", prompt, e);
                                return null;
                            }))
                    .toList();

            // Wait for all futures to complete and collect results
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            
            for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                try {
                    Map.Entry<String, String> result = future.get();
                    if (result != null) {
                        results.put(result.getKey(), result.getValue());
                    }
                } catch (Exception e) {
                    log.error("Error getting image generation result: {}", e.getMessage(), e);
                }
            }

            if (results.isEmpty()) {
                throw new ImageGeneratorServiceException("No images were generated successfully");
        }

            log.info("Successfully generated {} images in parallel using async I/O", results.size());
            return results;
        } catch (Exception e) {
            log.error("Parallel image generation failed: {}", e.getMessage(), e);
            throw new ImageGeneratorServiceException("Parallel image generation failed", e);
        }
    }

    /**
     * Generate a single image asynchronously using OkHttp async API
     * This is more efficient than blocking calls, especially for parallel requests
     * 
     * @param prompt The prompt for image generation
     * @return CompletableFuture that completes with base64-encoded image string
     */
    public CompletableFuture<String> callGeminiAPISingleAsync(String prompt) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
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
        
        // Request image output - IMPORTANT: Use both TEXT and IMAGE modalities
        // According to Gemini API docs, this is required for proper image generation
        JSONObject generationConfig = new JSONObject();
        JSONArray responseModalities = new JSONArray();
        responseModalities.put("TEXT");
        responseModalities.put("IMAGE");
        generationConfig.put("responseModalities", responseModalities);
        requestBody.put("generationConfig", generationConfig);

        String endpoint = String.format("%s/v1beta/models/%s:generateContent?key=%s", baseUrl, GEMINI_MODEL, apiKey);
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Gemini API call failed: {}", e.getMessage(), e);
                future.completeExceptionally(new ImageGeneratorServiceException(
                        "Gemini API call failed: " + e.getMessage(), e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBodyObj = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = "No error body provided";
                        if (responseBodyObj != null) {
                            errorBody = responseBodyObj.string();
                        }
                        log.error("Gemini API error response ({}): {}", response.code(), errorBody);
                        log.error("Requested endpoint: {}", endpoint);
                        future.completeExceptionally(new ImageGeneratorServiceException(
                                String.format(ERROR_MSG_TEMPLATE, response.code(), baseUrl)));
                        return;
                    }

                    if (responseBodyObj == null) {
                        future.completeExceptionally(new ImageGeneratorServiceException(
                                "Gemini API returned null response body"));
                        return;
                    }
                    
                    String responseBody = responseBodyObj.string();
                    if (responseBody == null || responseBody.isEmpty()) {
                        future.completeExceptionally(new ImageGeneratorServiceException(
                                "Gemini API returned empty response"));
                        return;
                    }

                    // Parse JSON response and extract base64 string
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    // Log the response structure for debugging
                    log.debug("Gemini API response structure: {}", jsonResponse.toString());
                    
                    if (jsonResponse.has("candidates") && jsonResponse.getJSONArray("candidates").length() > 0) {
                        JSONObject candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0);
                        if (candidate.has("content") && candidate.getJSONObject("content").has("parts")) {
                            JSONArray responseParts = candidate.getJSONObject("content").getJSONArray("parts");
                            for (int i = 0; i < responseParts.length(); i++) {
                                JSONObject part = responseParts.getJSONObject(i);
                                if (part.has("inlineData")) {
                                    future.complete(part.getJSONObject("inlineData").getString("data"));
                                    return;
                                }
                            }
                        }
                    }

                    // Check if API returned text instead of image (common when API can't generate image)
                    boolean hasTextResponse = false;
                    String textResponse = null;
                    if (jsonResponse.has("candidates") && jsonResponse.getJSONArray("candidates").length() > 0) {
                        JSONObject candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0);
                        if (candidate.has("content") && candidate.getJSONObject("content").has("parts")) {
                            JSONArray responseParts = candidate.getJSONObject("content").getJSONArray("parts");
                            for (int i = 0; i < responseParts.length(); i++) {
                                JSONObject part = responseParts.getJSONObject(i);
                                if (part.has("text")) {
                                    hasTextResponse = true;
                                    textResponse = part.getString("text");
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Log full response for debugging when no image is found
                    if (hasTextResponse) {
                        log.warn("Gemini API returned text instead of image. Text response: {}", textResponse);
                        log.warn("Full response: {}", responseBody);
                    } else {
                        log.error("No image found in Gemini API response. Full response: {}", responseBody);
                        log.error("Response structure - has candidates: {}, candidates length: {}", 
                                jsonResponse.has("candidates"),
                                jsonResponse.has("candidates") ? jsonResponse.getJSONArray("candidates").length() : 0);
                    }
                    
                    String errorMessage = hasTextResponse 
                            ? "Gemini API returned text instead of image. The API may not support image generation for this prompt."
                            : "No image found in Gemini API response. Check logs for full response details.";
                    
                    future.completeExceptionally(new ImageGeneratorServiceException(errorMessage));
                } catch (Exception e) {
                    log.error("Error processing Gemini API response: {}", e.getMessage(), e);
                    future.completeExceptionally(new ImageGeneratorServiceException(
                            "Error processing Gemini API response: " + e.getMessage(), e));
                }
            }
        });

        return future;
    }

    /**
     * Generate a single image using blocking call (for backward compatibility)
     * Note: For parallel requests, use callGeminiAPISingleAsync() instead
     */
    private String callGeminiAPISingle(String prompt) {
        try {
            return callGeminiAPISingleAsync(prompt).join();
        } catch (Exception e) {
            if (e.getCause() instanceof ImageGeneratorServiceException) {
                throw (ImageGeneratorServiceException) e.getCause();
            }
            throw new ImageGeneratorServiceException("Image generation failed: " + e.getMessage(), e);
        }
    }
    
    public String generateImage(String recipeName, List<String> ingredients, String description) {
        if (recipeName == null || recipeName.isEmpty()) {
            log.warn("Recipe name is empty or null");
            throw new ImageGeneratorServiceException("Recipe name is empty or null");
        }
        
        // Load and format the image generation prompt
        String ingredientsStr = (ingredients != null && !ingredients.isEmpty()) 
                ? String.join(", ", ingredients) 
                : "";
        String descriptionStr = description != null ? description : "";
        
        String prompt = promptLoader.loadAndFormatPrompt("image-generation.txt", Map.of(
                "RECIPE_NAME", recipeName,
                "INGREDIENTS", ingredientsStr,
                "DESCRIPTION", descriptionStr
        ));

        return callGeminiAPI(prompt);
    }

    /**
     * Generate multiple images in batch
     * @param imageRequests List of image generation requests
     * @return Map of recipe name to base64 image string
     */
    public Map<String, String> generateImagesBatch(List<ImageGenerationRequest> imageRequests) {
        if (imageRequests == null || imageRequests.isEmpty()) {
            throw new ImageGeneratorServiceException("Image requests list is empty or null");
        }

        List<String> prompts = new ArrayList<>();
        Map<String, String> requestToPrompt = new HashMap<>();

        // Build prompts for all requests
        for (ImageGenerationRequest request : imageRequests) {
            String ingredientsStr = (request.getIngredients() != null && !request.getIngredients().isEmpty()) 
                    ? String.join(", ", request.getIngredients()) 
                    : "";
            String descriptionStr = request.getDescription() != null ? request.getDescription() : "";
            
            String prompt = promptLoader.loadAndFormatPrompt("image-generation.txt", Map.of(
                    "RECIPE_NAME", request.getRecipeName(),
                    "INGREDIENTS", ingredientsStr,
                    "DESCRIPTION", descriptionStr
            ));
            
            prompts.add(prompt);
            requestToPrompt.put(request.getRecipeName(), prompt);
        }

        // Call batch API
        Map<String, String> promptToImage = callGeminiAPIBatch(prompts);
        
        // Map back to recipe names
        Map<String, String> result = new HashMap<>();
        for (ImageGenerationRequest request : imageRequests) {
            String prompt = requestToPrompt.get(request.getRecipeName());
            if (promptToImage.containsKey(prompt)) {
                result.put(request.getRecipeName(), promptToImage.get(prompt));
            }
        }

        return result;
    }

    // Inner class for batch requests
    public static class ImageGenerationRequest {
        private final String recipeName;
        private final List<String> ingredients;
        private final String description;

        public ImageGenerationRequest(String recipeName, List<String> ingredients, String description) {
            this.recipeName = recipeName;
            this.ingredients = ingredients;
            this.description = description;
        }

        public String getRecipeName() { return recipeName; }
        public List<String> getIngredients() { return ingredients; }
        public String getDescription() { return description; }
    }
}

