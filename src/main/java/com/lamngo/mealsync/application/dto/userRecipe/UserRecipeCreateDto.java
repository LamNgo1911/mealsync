package com.lamngo.mealsync.application.dto.userRecipe;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecipeCreateDto {
    @NotNull(message = "User ID must not be null")
    private Long userId;

    @NotNull(message = "Recipe ID must not be null")
    private Long recipeId;
}
