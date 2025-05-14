package com.lamngo.mealsync.application.service.AI;

import jakarta.transaction.Transactional;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class ImageGeneratorService {

    private final WebClient webClient;

    public ImageGeneratorService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.stability.ai/v1/generation/stable-diffusion-v1-5/text-to-image")
                .defaultHeader("Authorization", "Bearer YOUR_API_KEY")
                .build();
    }

    public String generateImage(String recipeName, List<String> ingredients, String description) {

        String prompt = String.format("A high-quality food photography image of %s, served with ingredients like %s. Styled in a realistic kitchen or restaurant setting.",
                recipeName,
                String.join(", ", ingredients));
        prompt += " " + description;

        return callStabilityAiAPI(prompt);
    }

    private String callStabilityAiAPI(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "text_prompts", List.of(Map.of("text", prompt)),
                "width", 512,
                "height", 512
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.build())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class) // Capture the response body (base64 or URL)
                .block(); // Blocking call for now
    }
}
