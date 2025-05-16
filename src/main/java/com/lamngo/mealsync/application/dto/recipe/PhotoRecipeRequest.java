package com.lamngo.mealsync.application.dto.recipe;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoRecipeRequest {

    @NotEmpty(message = "Image URL cannot be empty")
    @NotNull(message = "Image URL cannot be null")
    private String recipeName;

    @NotEmpty(message = "Ingredients list cannot be empty")
    @NotNull(message = "Ingredients list cannot be null")
    private List<String> ingredients;

    @NotEmpty(message = "Instructions cannot be empty")
    @NotNull(message = "Instructions cannot be null")
    private String description;
}
