package com.lamngo.mealsync.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipes")
@Getter
@Setter
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "Recipe name is required")
    private String name;

    @NotEmpty(message = "Ingredients are required")
    @ElementCollection
    @Column(name = "ingredient")
    private List<String> ingredients;

    @NotEmpty(message = "Instructions are required")
    private String instructions;

    @NotEmpty(message = "Cuisine is required")
    private String cuisine;

    private String imageUrl;

    private String ingredientKey;
}