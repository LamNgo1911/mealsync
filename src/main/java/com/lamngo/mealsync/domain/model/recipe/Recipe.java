package com.lamngo.mealsync.domain.model.recipe;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "recipes", indexes = {
        @Index(name = "idx_recipe_name", columnList = "name")
})
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "Recipe name cannot be blank")
    @Size(max = 150, message = "Recipe name cannot exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @NotBlank(message = "Instructions cannot be blank")
    @Column(nullable = false, columnDefinition = "TEXT") // Or use @Lob for large text
    private String instructions;

    @NotBlank(message = "Cuisine cannot be blank")
    @Size(max = 50, message = "Cuisine cannot exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String cuisine;

    @Size(max = 2048, message = "Image URL cannot exceed 2048 characters")
    @Column(length = 2048) // Allow null
    private String imageUrl;

    @Column(length = 255) // Allow null
    private String ingredientKey;
}