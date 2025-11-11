package com.lamngo.mealsync.application.dto.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserPreferenceRequestDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_withNormalArrays_success() throws Exception {
        String json = """
                {
                    "dietaryRestrictions": ["vegetarian", "gluten-free"],
                    "favoriteCuisines": ["Italian", "Mexican"],
                    "dislikedIngredients": ["mushroom", "olives"]
                }
                """;

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertEquals(2, dto.getDietaryRestrictions().size());
        assertTrue(dto.getDietaryRestrictions().contains("vegetarian"));
        assertTrue(dto.getDietaryRestrictions().contains("gluten-free"));

        assertEquals(2, dto.getFavoriteCuisines().size());
        assertTrue(dto.getFavoriteCuisines().contains("Italian"));
        assertTrue(dto.getFavoriteCuisines().contains("Mexican"));

        assertEquals(2, dto.getDislikedIngredients().size());
        assertTrue(dto.getDislikedIngredients().contains("mushroom"));
        assertTrue(dto.getDislikedIngredients().contains("olives"));
    }

    @Test
    void deserialize_withStringNone_returnsEmptyList() throws Exception {
        String json = """
                {
                    "dietaryRestrictions": "None",
                    "favoriteCuisines": "None",
                    "dislikedIngredients": "None"
                }
                """;

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertTrue(dto.getDietaryRestrictions().isEmpty());
        assertTrue(dto.getFavoriteCuisines().isEmpty());
        assertTrue(dto.getDislikedIngredients().isEmpty());
    }

    @Test
    void deserialize_withCommaSeparatedString_filtersNone() throws Exception {
        String json = """
                {
                    "dietaryRestrictions": "None, High-Protein",
                    "favoriteCuisines": "Italian, None, Mexican",
                    "dislikedIngredients": "mushroom, None"
                }
                """;

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertEquals(1, dto.getDietaryRestrictions().size());
        assertTrue(dto.getDietaryRestrictions().contains("High-Protein"));
        assertFalse(dto.getDietaryRestrictions().contains("None"));

        assertEquals(2, dto.getFavoriteCuisines().size());
        assertTrue(dto.getFavoriteCuisines().contains("Italian"));
        assertTrue(dto.getFavoriteCuisines().contains("Mexican"));
        assertFalse(dto.getFavoriteCuisines().contains("None"));

        assertEquals(1, dto.getDislikedIngredients().size());
        assertTrue(dto.getDislikedIngredients().contains("mushroom"));
        assertFalse(dto.getDislikedIngredients().contains("None"));
    }

    @Test
    void deserialize_withNullValues_returnsEmptyList() throws Exception {
        String json = """
                {
                    "dietaryRestrictions": null,
                    "favoriteCuisines": null,
                    "dislikedIngredients": null
                }
                """;

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertTrue(dto.getDietaryRestrictions().isEmpty());
        assertTrue(dto.getFavoriteCuisines().isEmpty());
        assertTrue(dto.getDislikedIngredients().isEmpty());
    }

    @Test
    void deserialize_withMissingFields_returnsEmptyList() throws Exception {
        String json = "{}";

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertTrue(dto.getDietaryRestrictions().isEmpty());
        assertTrue(dto.getFavoriteCuisines().isEmpty());
        assertTrue(dto.getDislikedIngredients().isEmpty());
    }

    @Test
    void deserialize_withEmptyString_returnsEmptyList() throws Exception {
        String json = """
                {
                    "dietaryRestrictions": "",
                    "favoriteCuisines": "",
                    "dislikedIngredients": ""
                }
                """;

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertTrue(dto.getDietaryRestrictions().isEmpty());
        assertTrue(dto.getFavoriteCuisines().isEmpty());
        assertTrue(dto.getDislikedIngredients().isEmpty());
    }

    @Test
    void deserialize_mixedFormats() throws Exception {
        String json = """
                {
                    "dietaryRestrictions": ["vegetarian"],
                    "favoriteCuisines": "Italian, Mexican",
                    "dislikedIngredients": "None"
                }
                """;

        UserPreferenceRequestDto dto = objectMapper.readValue(json, UserPreferenceRequestDto.class);

        assertEquals(1, dto.getDietaryRestrictions().size());
        assertTrue(dto.getDietaryRestrictions().contains("vegetarian"));

        assertEquals(2, dto.getFavoriteCuisines().size());
        assertTrue(dto.getFavoriteCuisines().contains("Italian"));
        assertTrue(dto.getFavoriteCuisines().contains("Mexican"));

        assertTrue(dto.getDislikedIngredients().isEmpty());
    }
}
