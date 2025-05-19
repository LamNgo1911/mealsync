package com.lamngo.mealsync.application.dto.userRecipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecipeReadDto {
    private UUID id;
    private UserReadDto user;
    private RecipeReadDto recipe;
    private String savedAt;
}
