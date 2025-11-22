package com.lamngo.mealsync.application.service.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GooglePlayBillingServiceTest {

    @Mock
    private IUserRepo userRepo;

    @InjectMocks
    private GooglePlayBillingService googlePlayBillingService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(googlePlayBillingService, "packageName", "com.cookify.mealsync");
        ReflectionTestUtils.setField(googlePlayBillingService, "serviceAccountKeyPath", "");

        // Use real User object for Google Play tests
        user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setEmail("test@example.com");
    }

    @Test
    void validateAndProcessPurchase_shouldProcessMonthlySubscription() {
        // Given
        String purchaseToken = "purchase-token-123";
        String productId = "premium_monthly";
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        googlePlayBillingService.validateAndProcessPurchase(user, purchaseToken, productId);

        // Then
        assertEquals(SubscriptionPlan.PREMIUM_MONTHLY, user.getSubscriptionPlan());
        assertEquals(SubscriptionStatus.ACTIVE, user.getSubscriptionStatus());
        assertEquals("google", user.getPaymentProvider().name());
        assertEquals(purchaseToken, user.getPaymentProviderId());
        assertNotNull(user.getSubscriptionStartDate());
        assertNotNull(user.getSubscriptionEndDate());
        
        // Verify end date is approximately 30 days from start
        long daysBetween = ChronoUnit.DAYS.between(
            user.getSubscriptionStartDate(), 
            user.getSubscriptionEndDate());
        assertEquals(30, daysBetween);
        
        verify(userRepo).save(user);
    }

    @Test
    void validateAndProcessPurchase_shouldProcessYearlySubscription() {
        // Given
        String purchaseToken = "purchase-token-456";
        String productId = "premium_yearly";
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        googlePlayBillingService.validateAndProcessPurchase(user, purchaseToken, productId);

        // Then
        assertEquals(SubscriptionPlan.PREMIUM_YEARLY, user.getSubscriptionPlan());
        assertEquals(SubscriptionStatus.ACTIVE, user.getSubscriptionStatus());
        
        // Verify end date is approximately 365 days from start
        long daysBetween = ChronoUnit.DAYS.between(
            user.getSubscriptionStartDate(), 
            user.getSubscriptionEndDate());
        assertEquals(365, daysBetween);
        
        verify(userRepo).save(user);
    }

    @Test
    void validateAndProcessPurchase_shouldThrowException_whenUnknownProductId() {
        // Given
        String purchaseToken = "purchase-token-789";
        String productId = "unknown_product";

        // When/Then
        assertThrows(BadRequestException.class, () -> 
            googlePlayBillingService.validateAndProcessPurchase(user, purchaseToken, productId));
        
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void validateAndProcessPurchase_shouldLogWarning_whenServiceAccountNotConfigured() {
        // Given
        ReflectionTestUtils.setField(googlePlayBillingService, "serviceAccountKeyPath", "");
        String purchaseToken = "purchase-token-123";
        String productId = "premium_monthly";
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        googlePlayBillingService.validateAndProcessPurchase(user, purchaseToken, productId);

        // Then
        // Should still process but log warning
        assertEquals(SubscriptionPlan.PREMIUM_MONTHLY, user.getSubscriptionPlan());
        verify(userRepo).save(user);
    }
}

