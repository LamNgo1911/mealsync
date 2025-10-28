package com.lamngo.mealsync.application.dto.recipe;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualIngredientDetectionRequest {

    @NotBlank(message = "Text input cannot be blank")
    private String textInput;
}