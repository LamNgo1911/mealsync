package com.lamngo.mealsync.application.dto.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientReadDto {

    private String name;
    private String quantity;
    private String unit;

}
