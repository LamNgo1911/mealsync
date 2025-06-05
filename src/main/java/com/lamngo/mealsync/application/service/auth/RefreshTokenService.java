package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.RefreshTokenReadDto;
import com.lamngo.mealsync.application.mapper.user.RefreshTokenMapper;
import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IRefreshTokenRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {
    @Value("${JWT_REFRESH_EXPIRATION}")
    private long JwtRefreshExpirationMs;

    private final IRefreshTokenRepo refreshTokenRepo;

    private final IUserRepo userRepo;

    private final RefreshTokenMapper refreshTokenMapper;

    public RefreshTokenService(IRefreshTokenRepo refreshTokenRepo, IUserRepo userRepo, RefreshTokenMapper refreshTokenMapper) {
        this.refreshTokenRepo = refreshTokenRepo;
        this.userRepo = userRepo;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    public RefreshTokenReadDto createRefreshToken(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(JwtRefreshExpirationMs));


        refreshTokenRepo.save(refreshToken);
        System.out.println(refreshToken);
        System.out.println(refreshTokenMapper.toRefreshTokenReadDto(refreshToken));
        return refreshTokenMapper.toRefreshTokenReadDto(refreshToken);
    }

    public boolean verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepo.deleteById(token.getId());
            return false;
        }
        return true;
    }

    public void deleteByUserId(UUID userId) {
        refreshTokenRepo.deleteByUserId(userId);
    }
}
