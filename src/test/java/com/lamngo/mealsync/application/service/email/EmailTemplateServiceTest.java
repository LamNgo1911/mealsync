package com.lamngo.mealsync.application.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateServiceTest {

    private EmailTemplateService emailTemplateService;

    @BeforeEach
    void setUp() {
        emailTemplateService = new EmailTemplateService();
    }

    @Test
    void loadTemplate_shouldLoadExistingTemplate() {
        // Given - template exists in resources
        String filename = "email-verification-success.html";

        // When
        String template = emailTemplateService.loadTemplate(filename);

        // Then
        assertNotNull(template);
        assertFalse(template.isEmpty());
        assertTrue(template.contains("<!DOCTYPE html>") || template.contains("<html"));
    }

    @Test
    void loadTemplate_shouldThrowException_whenTemplateNotFound() {
        // Given
        String filename = "non-existent-template.html";

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            emailTemplateService.loadTemplate(filename);
        });
        assertTrue(exception.getMessage().contains("Template file not found") ||
                   exception.getMessage().contains("Failed to load template file"));
    }

    @Test
    void formatTemplate_shouldReplacePlaceholders() {
        // Given
        String template = "Hello {NAME}, welcome to {APP_NAME}!";
        Map<String, String> variables = new HashMap<>();
        variables.put("NAME", "John");
        variables.put("APP_NAME", "Cookify");

        // When
        String result = emailTemplateService.formatTemplate(template, variables);

        // Then
        assertEquals("Hello John, welcome to Cookify!", result);
    }

    @Test
    void formatTemplate_shouldHandleNullValues() {
        // Given
        String template = "Hello {NAME}, your token is {TOKEN}";
        Map<String, String> variables = new HashMap<>();
        variables.put("NAME", "John");
        variables.put("TOKEN", null);

        // When
        String result = emailTemplateService.formatTemplate(template, variables);

        // Then
        assertEquals("Hello John, your token is ", result);
    }

    @Test
    void formatTemplate_shouldHandleMissingPlaceholders() {
        // Given
        String template = "Hello {NAME}, welcome!";
        Map<String, String> variables = new HashMap<>();
        variables.put("NAME", "John");

        // When
        String result = emailTemplateService.formatTemplate(template, variables);

        // Then
        assertEquals("Hello John, welcome!", result);
    }

    @Test
    void formatTemplate_shouldHandleMultipleOccurrences() {
        // Given
        String template = "{NAME} said hello to {NAME}";
        Map<String, String> variables = new HashMap<>();
        variables.put("NAME", "John");

        // When
        String result = emailTemplateService.formatTemplate(template, variables);

        // Then
        assertEquals("John said hello to John", result);
    }

    @Test
    void loadAndFormatTemplate_shouldLoadAndFormat() {
        // Given
        String filename = "email-verification-success.html";
        Map<String, String> variables = new HashMap<>();
        variables.put("LOGIN_URL", "https://example.com/login");
        variables.put("UNIVERSAL_LINK_URL", "https://cookify.dev/login");
        variables.put("MOBILE_DEEP_LINK", "cookify://login");
        variables.put("IS_MOBILE", "true");

        // When
        String result = emailTemplateService.loadAndFormatTemplate(filename, variables);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("https://example.com/login") || 
                  result.contains("https://cookify.dev/login"));
    }

    @Test
    void formatTemplate_shouldHandleEmptyMap() {
        // Given
        String template = "Hello {NAME}!";
        Map<String, String> variables = new HashMap<>();

        // When
        String result = emailTemplateService.formatTemplate(template, variables);

        // Then
        assertEquals("Hello {NAME}!", result);
    }

    @Test
    void formatTemplate_shouldHandleSpecialCharacters() {
        // Given
        String template = "Message: {MESSAGE}";
        Map<String, String> variables = new HashMap<>();
        variables.put("MESSAGE", "Hello & <world>");

        // When
        String result = emailTemplateService.formatTemplate(template, variables);

        // Then
        assertEquals("Message: Hello & <world>", result);
    }
}

