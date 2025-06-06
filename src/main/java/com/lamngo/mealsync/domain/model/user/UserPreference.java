package com.lamngo.mealsync.domain.model.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Dietary restrictions cannot be null")
    @ElementCollection
    private List<String> dietaryRestrictions;

    @NotNull(message = "Favorite cuisines cannot be null")
    @ElementCollection
    private List<String> favoriteCuisines;

    @NotNull(message = "Disliked ingredients cannot be null")
    @ElementCollection
    private List<String> dislikedIngredients;
}
