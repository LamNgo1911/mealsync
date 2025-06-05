package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.RefreshTokenReadDto;
import com.lamngo.mealsync.application.mapper.user.RefreshTokenMapper;
import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IRefreshTokenRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    @Mock
    private IRefreshTokenRepo refreshTokenRepo;
    @Mock
    private IUserRepo userRepo;
    @Mock
    private RefreshTokenMapper refreshTokenMapper;
    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        refreshTokenService = new RefreshTokenService(refreshTokenRepo, userRepo, refreshTokenMapper);
    }

    @Test
    void createRefreshToken_shouldReturnToken_whenUserExists() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        RefreshToken savedToken = new RefreshToken();
        when(refreshTokenRepo.save(any(RefreshToken.class))).thenReturn(savedToken);
        RefreshTokenReadDto dto = new RefreshTokenReadDto();
        when(refreshTokenMapper.toRefreshTokenReadDto(any(RefreshToken.class))).thenReturn(dto);
        RefreshTokenReadDto result = refreshTokenService.createRefreshToken(userId);
        assertNotNull(result);
        verify(refreshTokenRepo).save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_shouldThrowException_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> refreshTokenService.createRefreshToken(userId));
    }

    @Test
    void verifyExpiration_shouldReturnTrue_whenNotExpired() {
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().plusSeconds(60));
        boolean result = refreshTokenService.verifyExpiration(token);
        assertTrue(result);
        verify(refreshTokenRepo, never()).deleteById(any());
    }

    @Test
    void verifyExpiration_shouldReturnFalseAndDelete_whenExpired() {
        RefreshToken token = new RefreshToken();
        UUID tokenId = UUID.randomUUID();
        token.setId(tokenId);
        token.setExpiryDate(Instant.now().minusSeconds(60));
        boolean result = refreshTokenService.verifyExpiration(token);
        assertFalse(result);
        verify(refreshTokenRepo).deleteById(tokenId);
    }

    @Test
    void deleteByUserId_shouldCallRepo() {
        UUID userId = UUID.randomUUID();
        refreshTokenService.deleteByUserId(userId);
        verify(refreshTokenRepo).deleteByUserId(userId);
    }
}
