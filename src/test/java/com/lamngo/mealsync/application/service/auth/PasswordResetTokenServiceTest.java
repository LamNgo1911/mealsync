package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.domain.model.user.PasswordResetToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import com.lamngo.mealsync.domain.repository.user.IPasswordResetTokenRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock
    private IPasswordResetTokenRepo tokenRepo;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private PasswordResetTokenService passwordResetTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setRole(UserRole.USER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setEmailVerified(true);
    }

    @Test
    void createToken_shouldGenerateToken_whenUserProvided() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PasswordResetToken token = passwordResetTokenService.createToken(testUser);

        // Then
        assertNotNull(token);
        assertNotNull(token.getToken());
        assertFalse(token.getToken().isEmpty());
        assertEquals(testUser, token.getUser());
        assertFalse(token.isUsed());
        assertNotNull(token.getExpiryDate());
        assertTrue(token.getExpiryDate().isAfter(Instant.now()));
        verify(tokenRepo).deleteByUser(testUser);
        verify(tokenRepo).save(any(PasswordResetToken.class));
    }

    @Test
    void createToken_shouldDeleteExistingToken_whenUserHasToken() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetTokenService.createToken(testUser);

        // Then
        // The implementation always calls deleteByUser() first, then saves new token
        verify(tokenRepo).deleteByUser(testUser);
        verify(tokenRepo).save(any(PasswordResetToken.class));
    }

    @Test
    void createToken_shouldSetExpiryTo1Hour() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant beforeCreation = Instant.now();

        // When
        PasswordResetToken token = passwordResetTokenService.createToken(testUser);

        // Then
        Instant expectedExpiry = beforeCreation.plusSeconds(3600L);
        assertTrue(token.getExpiryDate().isAfter(expectedExpiry.minusSeconds(60)));
        assertTrue(token.getExpiryDate().isBefore(expectedExpiry.plusSeconds(60)));
    }

    @Test
    void validateToken_shouldThrowException_whenTokenNotFound() {
        // Given
        String invalidToken = "invalid-token";
        when(tokenRepo.findByToken(invalidToken)).thenReturn(Optional.empty());

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> passwordResetTokenService.validateToken(invalidToken));
        assertEquals("Invalid password reset token", exception.getMessage());
    }

    @Test
    void validateToken_shouldThrowException_whenTokenAlreadyUsed() {
        // Given
        String tokenString = "valid-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(true);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> passwordResetTokenService.validateToken(tokenString));
        assertEquals("Password reset token has already been used", exception.getMessage());
    }

    @Test
    void validateToken_shouldThrowException_whenTokenExpired() {
        // Given
        String tokenString = "expired-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(false);
        token.setExpiryDate(Instant.now().minusSeconds(3600)); // Expired 1 hour ago
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> passwordResetTokenService.validateToken(tokenString));
        assertEquals("Password reset token has expired", exception.getMessage());
    }

    @Test
    void validateToken_shouldReturnUser_whenTokenValid() {
        // Given
        String tokenString = "valid-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600)); // Valid for 1 more hour
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));

        // When
        User validatedUser = passwordResetTokenService.validateToken(tokenString);

        // Then
        assertNotNull(validatedUser);
        assertEquals(testUser, validatedUser);
    }

    @Test
    void markTokenAsUsed_shouldMarkTokenAsUsed() {
        // Given
        String tokenString = "valid-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));
        when(tokenRepo.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetTokenService.markTokenAsUsed(tokenString);

        // Then
        assertTrue(token.isUsed());
        verify(tokenRepo).save(token);
    }

    @Test
    void generateSecureToken_shouldGenerateUniqueTokens() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PasswordResetToken token1 = passwordResetTokenService.createToken(testUser);
        PasswordResetToken token2 = passwordResetTokenService.createToken(testUser);

        // Then
        assertNotEquals(token1.getToken(), token2.getToken());
        assertFalse(token1.getToken().isEmpty());
        assertFalse(token2.getToken().isEmpty());
    }
}

