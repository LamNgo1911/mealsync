package com.lamngo.mealsync.application.dto.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectedIngredientDto {
    private String name;
    private String quantity;  // Can be "200", "2 1/2", "a pinch", "1", etc.
    private String unit;      // Can be "grams", "cups", "pieces", "", etc.
}

