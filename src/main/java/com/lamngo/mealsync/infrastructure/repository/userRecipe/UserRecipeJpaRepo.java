package com.lamngo.mealsync.infrastructure.repository.userRecipe;

import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.UserRecipeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRecipeJpaRepo extends JpaRepository<UserRecipe, UUID> {

    UserRecipe save(UserRecipe userRecipe);

    List<UserRecipe> findAllByUserId(UUID userId);

    Page<UserRecipe> findAllByUserId(UUID userId, Pageable pageable);

    List<UserRecipe> findAllByUserIdAndType(UUID userId, UserRecipeType type);

    Page<UserRecipe> findAllByUserIdAndType(UUID userId, UserRecipeType type, Pageable pageable);

    Optional<UserRecipe> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

    Optional<UserRecipe> findByUserIdAndRecipeIdAndType(UUID userId, UUID recipeId, UserRecipeType type);

    void deleteByIdAndUserId(UUID userRecipeId, UUID userId);
}

