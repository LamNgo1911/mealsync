package com.lamngo.mealsync.application.dto.recipe;

import com.lamngo.mealsync.application.dto.user.UserPreferenceRequestDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRecipeRequest {

    @NotNull(message = "Ingredients list cannot be null")
    @NotEmpty(message = "Ingredients list cannot be empty")
    private List<DetectedIngredientDto> ingredients;

    private UserPreferenceRequestDto userPreference;
}
