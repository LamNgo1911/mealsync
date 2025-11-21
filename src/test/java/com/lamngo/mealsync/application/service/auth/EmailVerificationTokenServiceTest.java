package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.domain.model.user.EmailVerificationToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import com.lamngo.mealsync.domain.repository.user.IEmailVerificationTokenRepo;
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
class EmailVerificationTokenServiceTest {

    @Mock
    private IEmailVerificationTokenRepo tokenRepo;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private EmailVerificationTokenService emailVerificationTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setRole(UserRole.USER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setEmailVerified(false);
    }

    @Test
    void createToken_shouldGenerateToken_whenUserProvided() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        EmailVerificationToken token = emailVerificationTokenService.createToken(testUser);

        // Then
        assertNotNull(token);
        assertNotNull(token.getToken());
        assertFalse(token.getToken().isEmpty());
        assertEquals(testUser, token.getUser());
        assertFalse(token.isUsed());
        assertNotNull(token.getExpiryDate());
        assertTrue(token.getExpiryDate().isAfter(Instant.now()));
        verify(tokenRepo).deleteByUser(testUser);
        verify(tokenRepo).save(any(EmailVerificationToken.class));
    }

    @Test
    void createToken_shouldDeleteExistingToken_whenUserHasToken() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        emailVerificationTokenService.createToken(testUser);

        // Then
        // The implementation always calls deleteByUser() first, then saves new token
        verify(tokenRepo).deleteByUser(testUser);
        verify(tokenRepo).save(any(EmailVerificationToken.class));
    }

    @Test
    void createToken_shouldSetExpiryTo24Hours() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant beforeCreation = Instant.now();

        // When
        EmailVerificationToken token = emailVerificationTokenService.createToken(testUser);

        // Then
        Instant expectedExpiry = beforeCreation.plusSeconds(24 * 3600L);
        assertTrue(token.getExpiryDate().isAfter(expectedExpiry.minusSeconds(60)));
        assertTrue(token.getExpiryDate().isBefore(expectedExpiry.plusSeconds(60)));
    }

    @Test
    void verifyToken_shouldThrowException_whenTokenNotFound() {
        // Given
        String invalidToken = "invalid-token";
        when(tokenRepo.findByToken(invalidToken)).thenReturn(Optional.empty());

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> emailVerificationTokenService.verifyToken(invalidToken));
        assertEquals("Invalid verification token", exception.getMessage());
    }

    @Test
    void verifyToken_shouldThrowException_whenTokenAlreadyUsed() {
        // Given
        String tokenString = "valid-token";
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(true);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> emailVerificationTokenService.verifyToken(tokenString));
        assertEquals("Verification token has already been used", exception.getMessage());
    }

    @Test
    void verifyToken_shouldThrowException_whenTokenExpired() {
        // Given
        String tokenString = "expired-token";
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(false);
        token.setExpiryDate(Instant.now().minusSeconds(3600)); // Expired 1 hour ago
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> emailVerificationTokenService.verifyToken(tokenString));
        assertEquals("Verification token has expired", exception.getMessage());
    }

    @Test
    void verifyToken_shouldVerifyEmail_whenTokenValid() {
        // Given
        String tokenString = "valid-token";
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600)); // Valid for 1 more hour
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User verifiedUser = emailVerificationTokenService.verifyToken(tokenString);

        // Then
        assertNotNull(verifiedUser);
        assertTrue(verifiedUser.isEmailVerified());
        assertTrue(token.isUsed());
        verify(tokenRepo).save(token);
    }

    @Test
    void verifyToken_shouldMarkTokenAsUsed() {
        // Given
        String tokenString = "valid-token";
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenString);
        token.setUser(testUser);
        token.setUsed(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(tokenRepo.findByToken(tokenString)).thenReturn(Optional.of(token));
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        emailVerificationTokenService.verifyToken(tokenString);

        // Then
        assertTrue(token.isUsed());
        verify(tokenRepo).save(token);
    }

    @Test
    void generateSecureToken_shouldGenerateUniqueTokens() {
        // Given
        doNothing().when(tokenRepo).deleteByUser(testUser);
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        EmailVerificationToken token1 = emailVerificationTokenService.createToken(testUser);
        EmailVerificationToken token2 = emailVerificationTokenService.createToken(testUser);

        // Then
        assertNotEquals(token1.getToken(), token2.getToken());
        assertFalse(token1.getToken().isEmpty());
        assertFalse(token2.getToken().isEmpty());
    }
}

