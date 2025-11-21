package com.lamngo.mealsync.application.service.AI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptLoaderTest {

    private PromptLoader promptLoader;

    @BeforeEach
    void setUp() {
        promptLoader = new PromptLoader();
    }

    @Test
    void loadPrompt_shouldLoadExistingPrompt() {
        // Given - prompt exists in resources
        String filename = "recipe-generation.txt";

        // When
        String prompt = promptLoader.loadPrompt(filename);

        // Then
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }

    @Test
    void loadPrompt_shouldThrowException_whenPromptNotFound() {
        // Given
        String filename = "non-existent-prompt.txt";

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            promptLoader.loadPrompt(filename);
        });
        assertTrue(exception.getMessage().contains("Prompt file not found") ||
                   exception.getMessage().contains("Failed to load prompt file"));
    }

    @Test
    void formatPrompt_shouldReplacePlaceholders() {
        // Given
        String template = "Generate a recipe for {INGREDIENTS} with {CUISINE} style.";
        Map<String, String> variables = new HashMap<>();
        variables.put("INGREDIENTS", "chicken, rice");
        variables.put("CUISINE", "Italian");

        // When
        String result = promptLoader.formatPrompt(template, variables);

        // Then
        assertEquals("Generate a recipe for chicken, rice with Italian style.", result);
    }

    @Test
    void formatPrompt_shouldHandleNullValues() {
        // Given
        String template = "Recipe: {NAME}, Ingredients: {INGREDIENTS}";
        Map<String, String> variables = new HashMap<>();
        variables.put("NAME", "Test Recipe");
        variables.put("INGREDIENTS", null);

        // When
        String result = promptLoader.formatPrompt(template, variables);

        // Then
        assertEquals("Recipe: Test Recipe, Ingredients: ", result);
    }

    @Test
    void formatPrompt_shouldHandleMissingPlaceholders() {
        // Given
        String template = "Hello {NAME}!";
        Map<String, String> variables = new HashMap<>();
        variables.put("OTHER", "value");

        // When
        String result = promptLoader.formatPrompt(template, variables);

        // Then
        assertEquals("Hello {NAME}!", result);
    }

    @Test
    void formatPrompt_shouldHandleMultipleOccurrences() {
        // Given
        String template = "{ITEM} and {ITEM}";
        Map<String, String> variables = new HashMap<>();
        variables.put("ITEM", "apple");

        // When
        String result = promptLoader.formatPrompt(template, variables);

        // Then
        assertEquals("apple and apple", result);
    }

    @Test
    void loadAndFormatPrompt_shouldLoadAndFormat() {
        // Given
        String filename = "recipe-generation.txt";
        Map<String, String> variables = new HashMap<>();
        variables.put("INGREDIENTS", "test ingredients");

        // When
        String result = promptLoader.loadAndFormatPrompt(filename, variables);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void loadRecipeGenerationPrompt_shouldLoadPrompt() {
        // When
        String prompt = promptLoader.loadRecipeGenerationPrompt();

        // Then
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }

    @Test
    void loadRecipeGenerationBatchPrompt_shouldLoadPrompt() {
        // When
        String prompt = promptLoader.loadRecipeGenerationBatchPrompt();

        // Then
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }

    @Test
    void formatPrompt_shouldHandleEmptyMap() {
        // Given
        String template = "Template with {PLACEHOLDER}";
        Map<String, String> variables = new HashMap<>();

        // When
        String result = promptLoader.formatPrompt(template, variables);

        // Then
        assertEquals("Template with {PLACEHOLDER}", result);
    }

    @Test
    void formatPrompt_shouldHandleSpecialCharacters() {
        // Given
        String template = "Prompt: {CONTENT}";
        Map<String, String> variables = new HashMap<>();
        variables.put("CONTENT", "Special chars: & < > \" '");

        // When
        String result = promptLoader.formatPrompt(template, variables);

        // Then
        assertEquals("Prompt: Special chars: & < > \" '", result);
    }
}

