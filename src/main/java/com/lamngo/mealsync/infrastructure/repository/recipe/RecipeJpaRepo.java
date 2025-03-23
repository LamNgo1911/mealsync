package com.lamngo.mealsync.infrastructure.repository.recipe;

import com.lamngo.mealsync.domain.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeJpaRepo extends JpaRepository<Recipe, String> {
    Recipe save(Recipe recipe);
    Optional<Recipe> findById(String id);
    void deleteById(String id);
    void updateRecipe(Recipe recipe);
    List<Recipe> findAll();
    List<Recipe> findByUserId(String userId);
    List<Recipe> findByName(String name);
    List<Recipe> findByCategory(String category);
    List<Recipe> findByCuisine(String cuisine);
    List<Recipe> findByDiet(String diet);
}
