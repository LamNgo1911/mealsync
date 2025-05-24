package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.repository.user.IRefreshTokenRepo;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RefreshTokenRepo implements IRefreshTokenRepo {
    private final RefreshTokenJpaRepo refreshTokenJpaRepo;

    public RefreshTokenRepo(RefreshTokenJpaRepo refreshTokenJpaRepo) {
        this.refreshTokenJpaRepo = refreshTokenJpaRepo;
    }

    @Override
    public RefreshToken findByToken(String token) {
        return refreshTokenJpaRepo.findByToken(token);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepo.save(refreshToken);
    }

    @Override
    public void deleteById(UUID id) {
        refreshTokenJpaRepo.deleteById(id);

    }

    @Override
    public void deleteByUserId(UUID userId) {
        refreshTokenJpaRepo.deleteByUserId(userId);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        refreshTokenJpaRepo.deleteAllByUserId(userId);
    }

    @Override
    public RefreshToken findByUserId(UUID userId) {
        return refreshTokenJpaRepo.findByUserId(userId);
    }
}
