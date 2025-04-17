package com.lamngo.mealsync.application.dto.user;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceUpdateDto {

    @NotEmpty(message = "Dietary restrictions list cannot be empty if provided, but can be an empty list to clear.")
    private List<String> dietaryRestrictions;

    @NotEmpty(message = "Favorite cuisines list cannot be empty if provided, but can be an empty list to clear.")
    private List<String> favoriteCuisines;

    @NotEmpty(message = "Disliked ingredients list cannot be empty if provided, but can be an empty list to clear.")
    private List<String> dislikedIngredients;
}
