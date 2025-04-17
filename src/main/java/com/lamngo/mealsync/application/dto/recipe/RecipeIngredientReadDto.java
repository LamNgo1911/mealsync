package com.lamngo.mealsync.application.dto.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Includes @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientReadDto {

    private String name;
    private double quantity;
    private String unit;
    private String preparationNote;

    // Note: Excludes 'id' and 'recipe' fields from the entity
}
