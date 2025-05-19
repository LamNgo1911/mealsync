package com.lamngo.mealsync.application.dto.userRecipe;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecipeCreateDto {
    @NotNull(message = "User ID must not be null")
    private UUID userId;

    @NotNull(message = "Recipe ID must not be null")
    private UUID recipeId;
}
