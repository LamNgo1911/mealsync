package com.lamngo.mealsync.domain.model.mealPlan;

import com.lamngo.mealsync.domain.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a meal plan for a user.
 */
@Entity
@Table(name = "meal_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "days"})
@EqualsAndHashCode(exclude = {"user", "days"})
public class MealPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User must not be null")
    private User user;

    @NotNull(message = "Start date must not be null")
    @Column(nullable = false)
    private LocalDate startDate;

    @NotNull(message = "Number of days must not be null")
    @Positive(message = "Number of days must be positive")
    @Column(nullable = false)
    private Integer numberOfDays;

    @NotNull(message = "Meals per day must not be null")
    @Positive(message = "Meals per day must be positive")
    @Column(nullable = false)
    private Integer mealsPerDay;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private DietType dietType;

    @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @NotNull(message = "Meal plan days must not be null")
    @Size(min = 1, message = "Meal plan must have at least one day")
    @Builder.Default
    private List<MealPlanDay> days = new ArrayList<>();
}
