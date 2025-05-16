package com.lamngo.mealsync.domain.model;

import com.lamngo.mealsync.domain.model.mealPlan.MealPlan;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "meal_plan_recipes")
@Getter
@Setter
public class MealPlanRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan mealPlan;

    @ManyToOne
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    private String mealType; // e.g., breakfast, lunch
}