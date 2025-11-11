package com.lamngo.mealsync.application.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceRequestDto {

    @JsonDeserialize(using = StringToListDeserializer.class)
    private List<String> dietaryRestrictions;

    @JsonDeserialize(using = StringToListDeserializer.class)
    private List<String> favoriteCuisines;

    @JsonDeserialize(using = StringToListDeserializer.class)
    private List<String> dislikedIngredients;

    /**
     * Get dietary restrictions, returning an empty list if null
     */
    public List<String> getDietaryRestrictions() {
        return dietaryRestrictions != null ? dietaryRestrictions : new ArrayList<>();
    }

    /**
     * Get favorite cuisines, returning an empty list if null
     */
    public List<String> getFavoriteCuisines() {
        return favoriteCuisines != null ? favoriteCuisines : new ArrayList<>();
    }

    /**
     * Get disliked ingredients, returning an empty list if null
     */
    public List<String> getDislikedIngredients() {
        return dislikedIngredients != null ? dislikedIngredients : new ArrayList<>();
    }
}
