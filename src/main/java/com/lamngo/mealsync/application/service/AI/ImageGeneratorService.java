package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.ImageGeneratorServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ImageGeneratorService {

    private WebClient webClient;

    @Value("${STABILITY_API_KEY}")
    private String stabilityApiKey;

    @Value("${STABILITY_API_URL}")
    private String stabilityApiUrl;

    @PostConstruct
    public void init() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl(stabilityApiUrl)
                .defaultHeader("Authorization", "Bearer " + stabilityApiKey)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String callStabilityAiAPI(String prompt) {
        try {
            String response = webClient.post()
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
                    .doOnError(error -> log.error("Stability AI error: ", error))
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
        } catch (Exception e) {
            log.error("Image generation failed: {}", e.getMessage(), e);
            throw new ImageGeneratorServiceException("Image generation failed", e);
        }
    }

    public String generateImage(String recipeName, List<String> ingredients, String description) {
        if (recipeName == null || recipeName.isEmpty()) {
            log.warn("Recipe name is empty or null");
            throw new ImageGeneratorServiceException("Recipe name is empty or null");
        }
        if (ingredients == null) {
            ingredients = List.of();
        }
        String prompt = String.format(
                "Ultra-realistic, high-resolution DSLR food photo of %s made with %s. "
                        + "Freshly cooked, styled for a modern recipe app — clean, bright, and appetizing. "
                        + "Overhead (top-down) composition on a white or light neutral background. "
                        + "Soft natural daylight, gentle shadows, and realistic textures. "
                        + "Shot with a 50mm lens, shallow depth of field, and true-to-life colors. "
                        + "No text, no watermark, no illustration, no 3D render, no artificial look. %s",
                recipeName,
                String.join(", ", ingredients),
                description
        );

        return callStabilityAiAPI(prompt);
    }
}

