package com.lamngo.mealsync.application.service.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private IUserRepo userRepo;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private User user;

    @BeforeEach
    void setUp() {
        // Use mock to avoid MealPlan compilation issue
        user = mock(User.class);
        when(user.getId()).thenReturn(java.util.UUID.randomUUID());
        when(user.getEmail()).thenReturn("test@example.com");
    }

    @Test
    void initializeTrial_shouldSetTrialFields() {
        // Given - use a real User object for this test since we need to verify setters
        User realUser = new User();
        realUser.setId(java.util.UUID.randomUUID());
        realUser.setEmail("test@example.com");
        
        // When
        subscriptionService.initializeTrial(realUser);

        // Then
        assertEquals(SubscriptionPlan.TRIAL, realUser.getSubscriptionPlan());
        assertEquals(SubscriptionStatus.TRIAL, realUser.getSubscriptionStatus());
        assertNotNull(realUser.getAccountCreatedAt());
        assertNotNull(realUser.getTrialEndDate());
        assertEquals(999, realUser.getScansLimit());
        assertEquals(0, realUser.getScansUsed());
        assertEquals(com.lamngo.mealsync.domain.model.user.PaymentProvider.TRIAL, realUser.getPaymentProvider());
        
        // Verify trial end date is 3 days from now (within 1 second tolerance)
        Instant expectedEndDate = realUser.getAccountCreatedAt().plus(3, ChronoUnit.DAYS);
        long diffSeconds = Math.abs(realUser.getTrialEndDate().getEpochSecond() - expectedEndDate.getEpochSecond());
        assertTrue(diffSeconds < 2, "Trial end date should be approximately 3 days from account creation");
        
        verify(userRepo).save(realUser);
    }

    @Test
    void canScan_shouldReturnTrue_whenTrialActive() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.TRIAL);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.TRIAL);
        when(user.getTrialEndDate()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        boolean result = subscriptionService.canScan(user);

        // Then
        assertTrue(result);
        verify(userRepo, atLeastOnce()).save(any(User.class)); // checkTrialExpiration is called
    }

    @Test
    void canScan_shouldReturnFalse_whenTrialExpired() {
        // Given - use real user to verify status change
        User realUser = new User();
        realUser.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        realUser.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        realUser.setTrialEndDate(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userRepo.save(any(User.class))).thenReturn(realUser);

        // When
        boolean result = subscriptionService.canScan(realUser);

        // Then
        assertFalse(result);
        assertEquals(SubscriptionStatus.EXPIRED, realUser.getSubscriptionStatus());
        verify(userRepo, atLeastOnce()).save(realUser);
    }

    @Test
    void canScan_shouldReturnTrue_whenPremiumMonthlyActive() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_MONTHLY);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.ACTIVE);
        when(user.getSubscriptionEndDate()).thenReturn(Instant.now().plus(10, ChronoUnit.DAYS));

        // When
        boolean result = subscriptionService.canScan(user);

        // Then
        assertTrue(result);
    }

    @Test
    void canScan_shouldReturnFalse_whenPremiumMonthlyExpired() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_MONTHLY);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.ACTIVE);
        when(user.getSubscriptionEndDate()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));

        // When
        boolean result = subscriptionService.canScan(user);

        // Then
        assertFalse(result);
    }

    @Test
    void canScan_shouldReturnFalse_whenPremiumMonthlyCancelled() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_MONTHLY);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.CANCELLED);
        when(user.getSubscriptionEndDate()).thenReturn(Instant.now().plus(10, ChronoUnit.DAYS));

        // When
        boolean result = subscriptionService.canScan(user);

        // Then
        assertFalse(result);
    }

    @Test
    void canScan_shouldReturnTrue_whenPremiumYearlyActive() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_YEARLY);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.ACTIVE);
        when(user.getSubscriptionEndDate()).thenReturn(Instant.now().plus(100, ChronoUnit.DAYS));

        // When
        boolean result = subscriptionService.canScan(user);

        // Then
        assertTrue(result);
    }

    @Test
    void getTrialDaysRemaining_shouldReturnCorrectDays() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.TRIAL);
        when(user.getTrialEndDate()).thenReturn(Instant.now().plus(2, ChronoUnit.DAYS));

        // When
        long daysRemaining = subscriptionService.getTrialDaysRemaining(user);

        // Then
        assertEquals(2, daysRemaining);
    }

    @Test
    void getTrialDaysRemaining_shouldReturnZero_whenNotTrial() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_MONTHLY);

        // When
        long daysRemaining = subscriptionService.getTrialDaysRemaining(user);

        // Then
        assertEquals(0, daysRemaining);
    }

    @Test
    void getTrialDaysRemaining_shouldReturnZero_whenTrialExpired() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.TRIAL);
        when(user.getTrialEndDate()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));

        // When
        long daysRemaining = subscriptionService.getTrialDaysRemaining(user);

        // Then
        assertEquals(0, daysRemaining);
    }

    @Test
    void incrementScanUsage_shouldIncrementForTrial() {
        // Given - use real user to verify setter
        User realUser = new User();
        realUser.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        realUser.setScansUsed(5);
        when(userRepo.save(any(User.class))).thenReturn(realUser);

        // When
        subscriptionService.incrementScanUsage(realUser);

        // Then
        assertEquals(6, realUser.getScansUsed());
        verify(userRepo).save(realUser);
    }

    @Test
    void incrementScanUsage_shouldNotIncrementForPremium() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_MONTHLY);
        when(user.getScansUsed()).thenReturn(5);

        // When
        subscriptionService.incrementScanUsage(user);

        // Then
        verify(user, never()).setScansUsed(anyInt());
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void checkTrialExpiration_shouldExpireTrial_whenPastEndDate() {
        // Given - use real user to verify status change
        User realUser = new User();
        realUser.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        realUser.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        realUser.setTrialEndDate(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userRepo.save(any(User.class))).thenReturn(realUser);

        // When
        subscriptionService.checkTrialExpiration(realUser);

        // Then
        assertEquals(SubscriptionStatus.EXPIRED, realUser.getSubscriptionStatus());
        verify(userRepo).save(realUser);
    }

    @Test
    void checkTrialExpiration_shouldNotExpire_whenBeforeEndDate() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.TRIAL);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.TRIAL);
        when(user.getTrialEndDate()).thenReturn(Instant.now().plus(1, ChronoUnit.DAYS));

        // When
        subscriptionService.checkTrialExpiration(user);

        // Then
        verify(user, never()).setSubscriptionStatus(any());
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void checkTrialExpiration_shouldNotExpire_whenNotTrial() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.PREMIUM_MONTHLY);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.ACTIVE);
        when(user.getTrialEndDate()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));

        // When
        subscriptionService.checkTrialExpiration(user);

        // Then
        verify(user, never()).setSubscriptionStatus(any());
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void checkTrialExpiration_shouldNotExpire_whenAlreadyExpired() {
        // Given
        when(user.getSubscriptionPlan()).thenReturn(SubscriptionPlan.TRIAL);
        when(user.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED);
        when(user.getTrialEndDate()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));

        // When
        subscriptionService.checkTrialExpiration(user);

        // Then
        verify(user, never()).setSubscriptionStatus(any());
        verify(userRepo, never()).save(any(User.class));
    }
}

