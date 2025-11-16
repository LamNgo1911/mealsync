package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.ImageGeneratorServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ImageGeneratorService {

    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16MB
    private static final String ERROR_MSG_TEMPLATE = "Gemini 2.5 Flash Image API returned error %s. Endpoint: %s. " +
            "Please verify the API key and model name are correct.";
    private static final String GEMINI_MODEL = "gemini-2.5-flash-image";

    private WebClient webClient;
    private String baseUrl;
    private String apiKey;

    @Value("${GEMINI_API_KEY}")
    private String geminiApiKey;

    @Value("${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com}")
    private String geminiApiBaseUrl;
    
    private final PromptLoader promptLoader;
    
    public ImageGeneratorService(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    @PostConstruct
    public void init() {
        this.baseUrl = geminiApiBaseUrl;
        this.apiKey = geminiApiKey;
        
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
        
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
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
     * @param prompts List of prompts to generate images for
     * @return Map of prompt to base64 image string
     */
    public Map<String, String> callGeminiAPIBatch(List<String> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            throw new ImageGeneratorServiceException("Prompts list is empty or null");
        }

        try {
            // Use parallel streams to generate images concurrently
            // This is more efficient than sequential calls while working within API limitations
            Map<String, String> results = new HashMap<>();
            
            List<CompletableFuture<Map.Entry<String, String>>> futures = prompts.stream()
                    .map(prompt -> CompletableFuture.supplyAsync(() -> {
                        try {
                            String base64Image = callGeminiAPISingle(prompt);
                            return Map.entry(prompt, base64Image);
                        } catch (Exception e) {
                            log.error("Failed to generate image for prompt: {}", prompt, e);
                            return null;
                        }
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

            log.info("Successfully generated {} images in parallel", results.size());
            return results;
        } catch (Exception e) {
            log.error("Parallel image generation failed: {}", e.getMessage(), e);
            throw new ImageGeneratorServiceException("Parallel image generation failed", e);
        }
    }

    private String callGeminiAPISingle(String prompt) {
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
        
        // Request image output
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("responseModalities", new JSONArray().put("IMAGE"));
        requestBody.put("generationConfig", generationConfig);

        String endpoint = String.format("/v1beta/models/%s:generateContent?key=%s", GEMINI_MODEL, apiKey);
        
        String response = webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        this::handleErrorResponse)
                .bodyToMono(String.class)
                .doOnError(error -> log.error("Gemini API error: ", error))
                .onErrorResume(error -> Mono.empty())
                .block();

        if (response == null) {
            throw new ImageGeneratorServiceException("Gemini API call failed");
        }

        // Parse JSON response and extract base64 string
        JSONObject jsonResponse = new JSONObject(response);
        
        if (jsonResponse.has("candidates") && jsonResponse.getJSONArray("candidates").length() > 0) {
            JSONObject candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0);
            if (candidate.has("content") && candidate.getJSONObject("content").has("parts")) {
                JSONArray responseParts = candidate.getJSONObject("content").getJSONArray("parts");
                for (int i = 0; i < responseParts.length(); i++) {
                    JSONObject part = responseParts.getJSONObject(i);
                    if (part.has("inlineData")) {
                        return part.getJSONObject("inlineData").getString("data");
                    }
                }
            }
        }

        throw new ImageGeneratorServiceException("No image found in Gemini API response");
    }

    private Mono<ImageGeneratorServiceException> handleErrorResponse(
           ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("No error body provided")
                .doOnNext(errorBody -> {
                    log.error("Gemini API error response ({}): {}", 
                            clientResponse.statusCode(), errorBody);
                    log.error("Requested endpoint: {}", baseUrl);
                })
                .then(Mono.error(new ImageGeneratorServiceException(
                        String.format(ERROR_MSG_TEMPLATE, clientResponse.statusCode(), baseUrl))));
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

