package com.lamngo.mealsync.application.service.AI;

import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GeminiService {
    private static String apiKey = "YOUR_GEMINI_API_KEY";
    private static final String GEMINI_API_URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void setApiKey(String key) {
        apiKey = key;
    }

    /**
     * Generates a list of recipes using Gemini based on ingredients and user preferences.
     * @param ingredients List of ingredient names
     * @param userPreferences User preference string (e.g., dietary, cuisine)
     * @return Recipes as a formatted string
     * @throws IOException on network/API errors
     */
    public String generateRecipes(List<String> ingredients, String userPreferences) throws IOException {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("Ingredient list cannot be empty");
        }
        if (userPreferences == null) {
            userPreferences = "";
        }
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY")) {
            throw new IllegalStateException("Please set your Gemini API key using GeminiService.setApiKey() or directly in the source code");
        }
        String prompt = "Given these ingredients: " + String.join(", ", ingredients) +
                ". And these user preferences: " + userPreferences +
                ". Suggest 5 creative recipes with instructions and names.";

        JSONObject requestBody = new JSONObject();
        requestBody.put("contents", new JSONArray()
                .put(new JSONObject().put("parts", new JSONArray()
                        .put(new JSONObject().put("text", prompt)))));

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(GEMINI_API_URL_BASE + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gemini API call failed: HTTP " + response.code() + " - " + response.message());
            }
            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null) {
                throw new IOException("Empty response from Gemini API");
            }
            try {
                JSONObject json = new JSONObject(responseBody);
                if (!json.has("candidates") || json.getJSONArray("candidates").isEmpty()) {
                    throw new IOException("No candidates returned from Gemini API");
                }
                JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                if (!candidate.has("content")) {
                    throw new IOException("Malformed Gemini API response: missing content");
                }
                JSONObject content = candidate.getJSONObject("content");
                if (!content.has("parts") || content.getJSONArray("parts").isEmpty()) {
                    throw new IOException("Malformed Gemini API response: missing parts");
                }
                JSONObject part = content.getJSONArray("parts").getJSONObject(0);
                if (!part.has("text")) {
                    throw new IOException("Malformed Gemini API response: missing text");
                }
                return part.getString("text");
            } catch (JSONException e) {
                throw new IOException("Failed to parse Gemini API response: " + e.getMessage(), e);
            }
        }
    }
}
