package com.lamngo.mealsync.application.service.AI;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.MultipartBodyBuilder;
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

    @PostConstruct
    public void init() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer size, adjust as needed
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl("https://api.stability.ai/v1/generation/stable-diffusion-v1-6/text-to-image")
                .defaultHeader("Authorization", "Bearer " + stabilityApiKey)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private String callStabilityAiAPI(String prompt) {
        String response = webClient.post()
                .bodyValue(Map.of(
                        "text_prompts", List.of(Map.of("text", prompt)),
                        "cfg_scale", 7,
                        "height", 512,
                        "width", 512,
                        "samples", 1,
                        "steps", 30
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.error("Stability AI error: ", error))
                .onErrorResume(error -> Mono.empty())
                .block();

        // Parse JSON response and extract base64 string
        if (response != null) {
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray artifacts = jsonResponse.getJSONArray("artifacts");
            if (artifacts.length() > 0) {
                return artifacts.getJSONObject(0).getString("base64");
            } else {
                throw new RuntimeException("No artifacts found in Stability AI response");
            }
        } else {
            return null;
        }
    }

    public String generateImage(String recipeName, List<String> ingredients, String description) {
        String prompt = String.format(
                "A high-quality food photography image of %s, served with ingredients like %s. Styled in a realistic kitchen or restaurant setting. %s",
                recipeName,
                String.join(", ", ingredients),
                description
        );

        return callStabilityAiAPI(prompt);
    }
}
