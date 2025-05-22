package com.lamngo.mealsync.domain.repository.user;

import com.lamngo.mealsync.domain.model.user.RefreshToken;

import java.util.UUID;

public interface IRefreshTokenRepo {
    RefreshToken findByToken(String token);
    RefreshToken save(RefreshToken refreshToken);
    void deleteById(UUID id);
    void deleteByUserId(UUID userId);
    void deleteAllByUserId(UUID userId);
}
