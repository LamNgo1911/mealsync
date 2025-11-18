package com.lamngo.mealsync.infrastructure.config;

import com.lamngo.mealsync.application.service.AI.GeminiRecipeService;
import com.lamngo.mealsync.application.service.AI.IRecipeGenerationService;
import com.lamngo.mealsync.application.service.AI.OpenAIRecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for recipe generation service provider selection.
 * Allows switching between OpenAI and Gemini via the RECIPE_GENERATION_PROVIDER property.
 * 
 * Usage:
 * - Set RECIPE_GENERATION_PROVIDER=openai (default) to use OpenAI
 * - Set RECIPE_GENERATION_PROVIDER=gemini to use Gemini
 */
@Configuration
public class RecipeGenerationConfig {
    private static final Logger logger = LoggerFactory.getLogger(RecipeGenerationConfig.class);

    @Value("${RECIPE_GENERATION_PROVIDER:openai}")
    private String provider;

    /**
     * Provides the recipe generation service based on the configured provider.
     * 
     * @param openAIRecipeService OpenAI implementation
     * @param geminiRecipeService Gemini implementation
     * @return The selected recipe generation service
     */
    @Bean
    @Primary
    public IRecipeGenerationService recipeGenerationService(
            OpenAIRecipeService openAIRecipeService,
            GeminiRecipeService geminiRecipeService) {
        
        String normalizedProvider = provider != null ? provider.trim().toLowerCase() : "openai";
        
        IRecipeGenerationService selectedService;
        switch (normalizedProvider) {
            case "gemini":
                selectedService = geminiRecipeService;
                logger.info("✓ Recipe generation provider configured: GEMINI (gemini-2.5-flash)");
                break;
            case "openai":
            default:
                selectedService = openAIRecipeService;
                logger.info("✓ Recipe generation provider configured: OPENAI (gpt-4o-mini)");
                if (!"openai".equals(normalizedProvider)) {
                    logger.warn("Unknown provider '{}', defaulting to OpenAI", provider);
                }
                break;
        }
        
        return selectedService;
    }
}

