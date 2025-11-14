package com.lamngo.mealsync.application.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service for loading and formatting HTML email templates.
 */
@Service
@Slf4j
public class EmailTemplateService {
    
    private static final String TEMPLATES_DIR = "templates/";
    
    /**
     * Loads an HTML template from the templates directory.
     *
     * @param filename The name of the template file (e.g., "email-verification-success.html")
     * @return The template content
     * @throws RuntimeException if the template file cannot be loaded
     */
    public String loadTemplate(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATES_DIR + filename);
            if (!resource.exists()) {
                throw new RuntimeException("Template file not found: " + filename);
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Loaded HTML template from: {}", filename);
            return content;
        } catch (IOException e) {
            log.error("Failed to load template file: {}", filename, e);
            throw new RuntimeException("Failed to load template file: " + filename, e);
        }
    }
    
    /**
     * Formats a template by replacing placeholders with actual values.
     * Placeholders should be in the format {PLACEHOLDER_NAME}.
     *
     * @param template The template content
     * @param variables Map of placeholder names to their replacement values
     * @return The formatted template
     */
    public String formatTemplate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * Convenience method to load and format a template in one call.
     *
     * @param filename The name of the template file
     * @param variables Map of placeholder names to their replacement values
     * @return The formatted template
     */
    public String loadAndFormatTemplate(String filename, Map<String, String> variables) {
        String template = loadTemplate(filename);
        return formatTemplate(template, variables);
    }
}

