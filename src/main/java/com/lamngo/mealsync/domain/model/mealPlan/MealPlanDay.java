package com.lamngo.mealsync.domain.model.mealPlan;

import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meal_plan_days")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPlanDay {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull(message = "Date must not be null")
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    @NotNull(message = "MealPlanDay must belong to a MealPlan")
    private MealPlan mealPlan;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @NotNull(message = "Meals list must not be null")
    @Size(min = 1, message = "Each day must have at least one meal")
    private List<Meal> meals;
}
