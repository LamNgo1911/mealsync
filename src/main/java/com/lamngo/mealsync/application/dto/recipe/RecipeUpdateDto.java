package com.lamngo.mealsync.application.dto.recipe;

import jakarta.validation.constraints.Min;
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

    @NotEmpty(message = "Instructions cannot be empty if provided")
    private List<String> instructions;

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

    @Min(value = 0, message = "Preparation time must be non-negative")
    private Integer preparationTime;

    @Min(value = 0, message = "Cooking time must be non-negative")
    private Integer cookingTime;

    @Min(value = 0, message = "Total time must be non-negative")
    private Integer totalTime;

    @Min(value = 1, message = "Servings must be at least 1")
    private Integer servings;

    @Min(value = 0, message = "Calories cannot be negative")
    private Double calories;

    @Min(value = 0, message = "Protein cannot be negative")
    private Double protein;

    @Min(value = 0, message = "Carbohydrates cannot be negative")
    private Double carbohydrates;

    @Min(value = 0, message = "Fat cannot be negative")
    private Double fat;

    @Size(max = 50, message = "Difficulty cannot exceed 50 characters")
    private String difficulty;

    @Size(max = 10, message = "You can have up to 10 tags")
    private List<@Size(max = 30, message = "Each tag cannot exceed 30 characters") String> tags;
}