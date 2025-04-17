package com.lamngo.mealsync.domain.model.recipe;

import jakarta.persistence.*; // Import necessary JPA annotations
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
// Removed java.util.UUID as ID type is changed to Long

@Entity // Mark this class as a JPA entity
@Table(name = "recipe_ingredients") // Explicitly define the table name
@Getter
@Setter
@NoArgsConstructor // Add no-args constructor for JPA
public class RecipeIngredient { // Renamed from Ingredient for clarity

    @Id
    // Use IDENTITY or SEQUENCE for Long primary keys, AUTO is less specific here
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Changed ID type to Long, common for link entities

    // Many ingredients belong to one recipe
    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Ingredient must belong to a recipe
    @JoinColumn(name = "recipe_id", nullable = false) // Define the foreign key column
    private Recipe recipe;

    @NotBlank(message = "Ingredient name cannot be blank")
    @Size(max = 100, message = "Ingredient name cannot exceed 100 characters")
    @Column(nullable = false, length = 100) // Add column constraints
    private String name;

    @Positive(message = "Quantity must be positive")
    @Column(nullable = false) // Add column constraints
    private double quantity;

    @Size(max = 50, message = "Unit cannot exceed 50 characters")
    @Column(length = 50) // Unit is optional, add length constraint
    private String unit;

    @Size(max = 150, message = "Preparation note cannot exceed 150 characters")
    @Column(length = 150) // Preparation note is optional, add length constraint
    private String preparationNote;

    // --- equals(), hashCode(), toString() ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecipeIngredient that = (RecipeIngredient) o;
        // Use ID for equality check once persisted
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // Consistent hashCode based on class, safe for JPA entities
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        // Provide a concise representation
        return "RecipeIngredient{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                '}';
    }
}