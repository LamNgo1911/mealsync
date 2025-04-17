package com.lamngo.mealsync.application.dto.recipe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Includes @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientCreateDto {

    @NotBlank(message = "Ingredient name cannot be blank")
    @Size(max = 100, message = "Ingredient name cannot exceed 100 characters")
    private String name;

    @Positive(message = "Quantity must be positive")
    private double quantity; // Consider if specific precision validation is needed

    @Size(max = 50, message = "Unit cannot exceed 50 characters")
    private String unit; // Optional field

    @Size(max = 150, message = "Preparation note cannot exceed 150 characters")
    private String preparationNote; // Optional field
}
