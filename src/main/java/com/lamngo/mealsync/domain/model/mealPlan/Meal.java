package com.lamngo.mealsync.domain.model.mealPlan;

import com.lamngo.mealsync.domain.model.recipe.Recipe;
import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.*;

import java.util.UUID;

@Entity
@Table(name = "meals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meal {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Meal type must not be null")
    private MealType type; // BREAKFAST, LUNCH, DINNER

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    @NotNull(message = "Meal must belong to a day")
    private MealPlanDay day;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;
}
