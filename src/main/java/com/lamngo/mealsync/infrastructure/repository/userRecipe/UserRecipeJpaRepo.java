package com.lamngo.mealsync.infrastructure.repository.userRecipe;

import com.lamngo.mealsync.domain.model.UserRecipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRecipeJpaRepo extends JpaRepository<UserRecipe, UUID> {

    List<UserRecipe> findAllByUserId(UUID userId);

    Optional<UserRecipe> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

    void deleteByIdAndUserId(UUID userRecipeId, UUID userId);
}

