package com.lamngo.mealsync.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "preferences")
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotEmpty(message = "Dietary restrictions cannot be empty")
    @ElementCollection
    private List<String> dietaryRestrictions;

    @NotEmpty(message = "Favorite cuisines cannot be empty")
    @ElementCollection
    private List<String> favoriteCuisines;

    @NotEmpty(message = "Disliked ingredients cannot be empty")
    @ElementCollection
    private List<String> dislikedIngredients;
}
