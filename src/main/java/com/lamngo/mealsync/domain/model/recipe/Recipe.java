package com.lamngo.mealsync.domain.model.recipe;

import com.lamngo.mealsync.domain.model.UserRecipe;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.lamngo.mealsync.domain.model.user.User;

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
    @NotNull
    private UUID id;

    @NotBlank(message = "Recipe name cannot be blank")
    @Size(max = 150, message = "Recipe name cannot exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @NotEmpty(message = "Ingredients list cannot be empty")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recipe_instructions", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "instruction", columnDefinition = "TEXT")
    @Size(min = 1, message = "At least one instruction step is required")
    private List<String> instructions = new ArrayList<>();


    @NotBlank(message = "Cuisine cannot be blank")
    @Size(max = 50, message = "Cuisine cannot exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String cuisine;

    @Size(max = 2048, message = "Image URL cannot exceed 2048 characters")
    @Column(length = 2048)
    private String imageUrl;

    @NotBlank(message = "Ingredient key cannot be blank")
    @Size(max = 255, message = "Ingredient key cannot exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String ingredientKey;

    @NotBlank(message = "Description cannot be blank")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Column(nullable = false, length = 500)
    private String description;

    @NotNull(message = "Preparation time is required")
    @Min(value = 0, message = "Preparation time cannot be negative")
    @Column(nullable = false)
    private Integer preparationTime;

    @NotNull(message = "Cooking time is required")
    @Min(value = 0, message = "Cooking time cannot be negative")
    @Column(nullable = false)
    private Integer cookingTime;

    @NotNull(message = "Total time is required")
    @Min(value = 0, message = "Total time cannot be negative")
    @Column(nullable = false)
    private Integer totalTime;

    @NotNull(message = "Servings are required")
    @Min(value = 1, message = "Servings must be at least 1")
    @Column(nullable = false)
    private Integer servings;

    @NotBlank(message = "Difficulty is required")
    @Size(max = 20, message = "Difficulty cannot exceed 20 characters")
    @Column(nullable = false, length = 20)
    private String difficulty;

    @ElementCollection
    @CollectionTable(name = "recipe_tags", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "tag", length = 50, nullable = false)
    @Size(min = 1, message = "At least one tag is required")
    private List<@NotBlank String> tags = new ArrayList<>();

    @NotBlank(message = "Source cannot be blank")
    @Size(max = 50, message = "Source cannot exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String source = "AI";

    @Column(nullable = false, updatable = false)
    @NotNull
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRecipe> userRecipes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
