package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IRefreshTokenRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.UUID;

public class RefreshTokenService {
    @Value("${JWT_REFRESH_EXPIRATION}")
    private long JwtRefreshExpirationMs;

    private final IRefreshTokenRepo refreshTokenRepo;

    private final IUserRepo userRepo;

    public RefreshTokenService(IRefreshTokenRepo refreshTokenRepo, IUserRepo userRepo) {
        this.refreshTokenRepo = refreshTokenRepo;
        this.userRepo = userRepo;
    }

    public RefreshToken createRefreshToken(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(JwtRefreshExpirationMs));

        return refreshTokenRepo.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepo.deleteById(token.getId());
            throw new BadRequestException("Refresh token expired. Please log in again.");
        }
        return token;
    }

    public void deleteByUserId(UUID userId) {
        refreshTokenRepo.deleteByUserId(userId);
    }
}
