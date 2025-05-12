package com.lamngo.mealsync.domain.model.recipe;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recipe_ingredients")
@Getter
@Setter
@NoArgsConstructor
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @NotBlank(message = "Ingredient name cannot be blank")
    @Size(max = 100, message = "Ingredient name cannot exceed 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Quantity cannot be blank")
    @Size(max = 50, message = "Quantity cannot exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String quantity;  // String to support "1", "2 1/2", "a pinch", etc.

    @Size(max = 50, message = "Unit cannot exceed 50 characters")
    @Column(length = 50)
    private String unit;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeIngredient that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RecipeIngredient{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", quantity='" + quantity + '\'' +
                ", unit='" + unit + '\'' +
                '}';
    }
}
