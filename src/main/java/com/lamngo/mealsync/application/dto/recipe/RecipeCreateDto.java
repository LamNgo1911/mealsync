package com.lamngo.mealsync.application.dto.recipe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeCreateDto {

    @NotBlank(message = "Recipe name cannot be blank")
    @Size(max = 100, message = "Recipe name cannot exceed 100 characters")
    private String name;

    @NotEmpty(message = "Ingredients list cannot be empty")
    private List<RecipeIngredientReadDto> ingredients;

    @NotBlank(message = "Instructions cannot be blank")
    @Size(max = 5000, message = "Instructions cannot exceed 5000 characters")
    private String instructions;

    @NotBlank(message = "Cuisine cannot be blank")
    @Size(max = 50, message = "Cuisine cannot exceed 50 characters")
    private String cuisine;

    @URL(message = "Image URL must be a valid URL")
    @Size(max = 2048, message = "Image URL cannot exceed 2048 characters")
    private String imageUrl;
}