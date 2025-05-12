package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeIngredientJpaRepo extends JpaRepository<RecipeIngredient, UUID> {
    List<RecipeIngredient> findAllByRecipeId(UUID recipeId);
    void deleteById(UUID id);
    Optional<RecipeIngredient> findById(UUID id);
    RecipeIngredient save(RecipeIngredient recipeIngredient);
}
