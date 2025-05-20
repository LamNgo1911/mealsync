package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeJpaRepo extends JpaRepository<Recipe, UUID> {
    Recipe save(Recipe recipe);
    Optional<Recipe> findById(UUID id);
    void deleteById(UUID id);
    Optional<Recipe> findByIngredientKey(String ingredientKey);
}
