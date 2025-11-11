package com.lamngo.mealsync.application.dto.user;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom deserializer that handles both string and array inputs for List<String> fields.
 * Converts comma-separated strings to lists and filters out "None" values.
 */
public class StringToListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node == null || node.isNull()) {
            return new ArrayList<>();
        }

        // Handle string input (e.g., "None", "None, High-Protein", "item1, item2")
        if (node.isTextual()) {
            String text = node.asText().trim();

            // Return empty list for empty string
            if (text.isEmpty()) {
                return new ArrayList<>();
            }

            // Split by comma and filter out "None" values
            return Arrays.stream(text.split("\\s*,\\s*"))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty() && !item.equalsIgnoreCase("None"))
                    .collect(Collectors.toList());
        }

        // Handle array input (normal JSON array)
        if (node.isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    String value = element.asText().trim();
                    // Filter out "None" values even from arrays
                    if (!value.isEmpty() && !value.equalsIgnoreCase("None")) {
                        result.add(value);
                    }
                }
            }
            return result;
        }

        // Default to empty list for any other type
        return new ArrayList<>();
    }
}
