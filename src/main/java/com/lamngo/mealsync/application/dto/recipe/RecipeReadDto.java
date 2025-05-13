package com.lamngo.mealsync.application.dto.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeReadDto {

    private UUID id;
    private String name;
    private List<RecipeIngredientReadDto> ingredients;
    private List<String> instructions;
    private String cuisine;
    private String imageUrl;
    private String ingredientKey;
    private String description;
    private Integer preparationTime;
    private Integer cookingTime;
    private Integer totalTime;
    private Integer servings;
    private String difficulty;
    private List<String> tags;
    private String source;
    private Instant createdAt;
    private Instant updatedAt;
}