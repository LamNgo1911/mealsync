package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.UserRecipeType;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserRecipeRepo {
     UserRecipe saveUserRecipe(UserRecipe userRecipe);
     List<UserRecipe> getUserRecipesByUserId(UUID userId);
     Page<UserRecipe> getUserRecipesByUserId(UUID userId, OffsetPage pageable);
     List<UserRecipe> getUserRecipesByUserIdAndType(UUID userId, UserRecipeType type);
     Page<UserRecipe> getUserRecipesByUserIdAndType(UUID userId, UserRecipeType type, OffsetPage pageable);
     Optional<UserRecipe> getUserRecipeByUserIdAndRecipeIdAndType(UUID userId, UUID recipeId, UserRecipeType type);
     void deleteUserRecipe(UUID userRecipeId);
}
