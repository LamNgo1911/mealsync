package com.lamngo.mealsync.application.service.AI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility class for loading and formatting prompt templates from resources.
 */
@Component
public class PromptLoader {
    private static final Logger logger = LoggerFactory.getLogger(PromptLoader.class);
    private static final String PROMPTS_DIR = "prompts/";

    /**
     * Loads a prompt template from the prompts directory.
     *
     * @param filename The name of the prompt file (e.g., "recipe-generation.txt")
     * @return The prompt template content
     * @throws RuntimeException if the prompt file cannot be loaded
     */
    public String loadPrompt(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPTS_DIR + filename);
            if (!resource.exists()) {
                throw new RuntimeException("Prompt file not found: " + filename);
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            logger.debug("Loaded prompt template from: {}", filename);
            return content;
        } catch (IOException e) {
            logger.error("Failed to load prompt file: {}", filename, e);
            throw new RuntimeException("Failed to load prompt file: " + filename, e);
        }
    }

    /**
     * Formats a prompt template by replacing placeholders with actual values.
     * Placeholders should be in the format {PLACEHOLDER_NAME}.
     *
     * @param template The prompt template
     * @param variables Map of placeholder names to their replacement values
     * @return The formatted prompt
     */
    public String formatPrompt(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * Convenience method to load and format a prompt in one call.
     *
     * @param filename The name of the prompt file
     * @param variables Map of placeholder names to their replacement values
     * @return The formatted prompt
     */
    public String loadAndFormatPrompt(String filename, Map<String, String> variables) {
        String template = loadPrompt(filename);
        return formatPrompt(template, variables);
    }
}

