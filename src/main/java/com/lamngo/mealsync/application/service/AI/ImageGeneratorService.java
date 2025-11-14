package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.ImageGeneratorServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ImageGeneratorService {

    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16MB
    private static final String ERROR_MSG_TEMPLATE = "Stability AI v2beta API returned error %s. Endpoint: %s. " +
            "Please verify the model name is correct. " +
            "Supported models: sd3, sd3.5-flash, sd3.5-medium, sd3.5-large, sd3.5-large-turbo. " +
            "Check Stability AI docs for available models.";

    private WebClient webClient;
    private boolean isV2Beta;
    private String normalizedModelName;
    private String baseUrl;

    @Value("${STABILITY_API_KEY}")
    private String stabilityApiKey;

    @Value("${STABILITY_API_URL}")
    private String stabilityApiUrl;
    
    private final PromptLoader promptLoader;
    
    public ImageGeneratorService(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    @PostConstruct
    public void init() {
        this.isV2Beta = StabilityApiUrlHelper.isV2BetaApi(stabilityApiUrl);
        
        if (isV2Beta) {
            String modelName = StabilityApiUrlHelper.extractModelFromUrl(stabilityApiUrl);
            this.normalizedModelName = (modelName != null && !modelName.isEmpty()) 
                    ? StabilityApiUrlHelper.normalizeModelName(modelName) 
                    : null;
            this.baseUrl = StabilityApiUrlHelper.getBaseUrlForV2Beta(stabilityApiUrl);
        } else {
            this.baseUrl = stabilityApiUrl;
            this.normalizedModelName = null;
        }
        
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
        
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + stabilityApiKey)
                .build();
    }

    public String callStabilityAiAPI(String prompt) {
        try {
            if (isV2Beta) {
                return callV2BetaAPI(prompt);
            } else {
                return callV1API(prompt);
            }
        } catch (ImageGeneratorServiceException e) {
            // Re-throw service exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Image generation failed: {}", e.getMessage(), e);
            throw new ImageGeneratorServiceException("Image generation failed", e);
        }
    }
    
    private String callV1API(String prompt) {
        String response = webClient.post()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .bodyValue(Map.of(
                        "text_prompts", List.of(Map.of("text", prompt)),
                        "cfg_scale", 7,
                        "height", 1024,
                        "width", 1024,
                        "samples", 1,
                        "steps", 30
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.error("Stability AI v1 error: ", error))
                .onErrorResume(error -> Mono.empty())
                .block();

        if (response == null) {
            throw new ImageGeneratorServiceException("Stability AI API call failed");
        }

        // Parse JSON response and extract base64 string
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray artifacts = jsonResponse.getJSONArray("artifacts");

        if (artifacts.length() == 0) {
            throw new ImageGeneratorServiceException("No artifacts found in Stability AI response");
        }

        return artifacts.getJSONObject(0).getString("base64");
    }
    
    private String callV2BetaAPI(String prompt) {
        MultiValueMap<String, HttpEntity<?>> parts = buildMultipartBody(prompt);
        
        Flux<DataBuffer> response = webClient.post()
                .header("Accept", "image/*")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                        this::handleErrorResponse)
                .bodyToFlux(DataBuffer.class)
                .doOnError(error -> log.error("Stability AI v2beta error: ", error));

        byte[] imageBytes = DataBufferUtils.join(response)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .doOnError(error -> log.error("Error reading v2beta response: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty())
                .block();

        if (imageBytes == null || imageBytes.length == 0) {
            throw new ImageGeneratorServiceException("Stability AI v2beta API call failed or returned empty response");
        }

        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private MultiValueMap<String, HttpEntity<?>> buildMultipartBody(String prompt) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("prompt", prompt);
        builder.part("mode", "text-to-image");
        builder.part("aspect_ratio", "1:1");
        builder.part("output_format", "png");
        if (normalizedModelName != null) {
            builder.part("model", normalizedModelName);
            log.debug("Using model: {} from URL", normalizedModelName);
        }
        return builder.build();
    }

    private Mono<ImageGeneratorServiceException> handleErrorResponse(
           ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("No error body provided")
                .doOnNext(errorBody -> {
                    log.error("Stability AI v2beta API error response ({}): {}", 
                            clientResponse.statusCode(), errorBody);
                    log.error("Requested endpoint: {}", stabilityApiUrl);
                })
                .then(Mono.error(new ImageGeneratorServiceException(
                        String.format(ERROR_MSG_TEMPLATE, clientResponse.statusCode(), stabilityApiUrl))));
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

        return callStabilityAiAPI(prompt);
    }
}

