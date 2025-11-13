package com.lamngo.mealsync.application.dto.recipe;

import jakarta.validation.Valid;
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
public class ManualIngredientDetectionRequest {

    @NotEmpty(message = "Ingredients list cannot be empty")
    @Valid
    private List<DetectedIngredientDto> ingredients;
}