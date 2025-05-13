package com.lamngo.mealsync.application.service.AI;

import jakarta.transaction.Transactional;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class ImageGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(ImageGeneratorService.class);

    @Value("${OPENAI_API_KEY}")
    private String openAiApiKey;

    @Value("${OPENAI_API_URL}")
    private String openAiApiUrl;

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public String generateImageUrl(String recipeName, List<String> ingredients, String description) {
        String prompt = String.format("A high-quality food photography image of %s, served with ingredients like %s. Styled in a realistic kitchen or restaurant setting.",
                recipeName,
                String.join(", ", ingredients));
        prompt += " " + description;

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "dall-e-3"); // Use "dall-e-2" for cheaper option
        requestBody.put("prompt", prompt);
        requestBody.put("n", 1);
        requestBody.put("size", "1024x1024");
        requestBody.put("response_format", "url");

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(openAiApiUrl)
                .addHeader("Authorization", "Bearer " + openAiApiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("DALL·E image generation failed: {}", response.message());
                throw new IOException("DALL·E API error: " + response.message());
            }

            String responseJson = response.body().string();
            JSONObject json = new JSONObject(responseJson);
            JSONArray data = json.getJSONArray("data");
            if (data.length() > 0) {
                return data.getJSONObject(0).getString("url");
            } else {
                throw new IOException("No image URL returned from OpenAI");
            }
        } catch (IOException e) {
            logger.error("Error generating image: {}", e.getMessage());
            return null;
        }
    }
}
