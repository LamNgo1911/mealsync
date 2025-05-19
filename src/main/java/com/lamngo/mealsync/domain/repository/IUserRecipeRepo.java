package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.UserRecipe;

import java.util.List;
import java.util.UUID;

public interface IUserRecipeRepo {
     UserRecipe saveUserRecipe(UserRecipe userRecipe);
     List<UserRecipe> getUserRecipesByUserId(UUID userId);
     void deleteUserRecipe(UUID userRecipeId);
}
