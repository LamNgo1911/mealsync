package com.lamngo.mealsync.infrastructure.repository.userRecipe;

import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.UserRecipeType;
import com.lamngo.mealsync.domain.repository.IUserRecipeRepo;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRecipeRepo implements IUserRecipeRepo {
    private final UserRecipeJpaRepo userRecipeJpaRepo;

    public UserRecipeRepo(UserRecipeJpaRepo userRecipeJpaRepo) {
        this.userRecipeJpaRepo = userRecipeJpaRepo;
    }

    @Override
    public UserRecipe saveUserRecipe(UserRecipe userRecipe) {
        return  userRecipeJpaRepo.save(userRecipe);
    }

    @Override
    public List<UserRecipe> getUserRecipesByUserId(UUID userId) {
        return userRecipeJpaRepo.findAllByUserId(userId);
    }

    @Override
    public List<UserRecipe> getUserRecipesByUserIdAndType(UUID userId, UserRecipeType type) {
        return userRecipeJpaRepo.findAllByUserIdAndType(userId, type);
    }

    @Override
    public Optional<UserRecipe> getUserRecipeByUserIdAndRecipeIdAndType(UUID userId, UUID recipeId, UserRecipeType type) {
        return userRecipeJpaRepo.findByUserIdAndRecipeIdAndType(userId, recipeId, type);
    }

    @Override
    public void deleteUserRecipe(UUID userRecipeId) {
        userRecipeJpaRepo.deleteById(userRecipeId);
    }
}
