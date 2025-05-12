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
public class RecipeUpdateDto {

    @NotBlank(message = "Recipe name cannot be blank if provided")
    @Size(max = 100, message = "Recipe name cannot exceed 100 characters")
    private String name;

    @NotEmpty(message = "Ingredients list cannot be empty if provided")
    private List<RecipeIngredientReadDto> ingredients;

    @NotBlank(message = "Instructions cannot be blank if provided")
    @Size(max = 5000, message = "Instructions cannot exceed 5000 characters")
    private String instructions;

    @NotBlank(message = "Cuisine cannot be blank if provided")
    @Size(max = 50, message = "Cuisine cannot exceed 50 characters")
    private String cuisine;

    @URL(message = "Invalid URL format for image if provided")
    @Size(max = 2048, message = "Image URL cannot exceed 2048 characters")
    private String imageUrl;

    @NotBlank(message = "Ingredient key cannot be blank if provided")
    @Size(max = 255, message = "Ingredient key cannot exceed 255 characters")
    private String ingredientKey;

    @NotBlank(message = "Description cannot be blank if provided")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private Integer preparationTime;
    private Integer cookingTime;
    private Integer totalTime;
    private Integer servings;
    private String difficulty;
    private List<String> tags;
    private String source;
}