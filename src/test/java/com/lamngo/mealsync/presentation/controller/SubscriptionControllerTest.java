package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.subscription.ApplePurchaseRequest;
import com.lamngo.mealsync.application.dto.subscription.GooglePurchaseRequest;
import com.lamngo.mealsync.application.dto.subscription.SubscriptionStatusDto;
import com.lamngo.mealsync.application.service.subscription.AppleIAPService;
import com.lamngo.mealsync.application.service.subscription.GooglePlayBillingService;
import com.lamngo.mealsync.application.service.subscription.SubscriptionService;
import com.lamngo.mealsync.domain.model.user.*;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private AppleIAPService appleIAPService;

    @Mock
    private GooglePlayBillingService googlePlayBillingService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private IUserRepo userRepo;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private User user;

    @BeforeEach
    void setUp() {
        // Use real User object for controller tests
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        user.setTrialEndDate(Instant.now().plus(2, ChronoUnit.DAYS));
        user.setScansUsed(0);
        user.setScansLimit(999);
        user.setPaymentProvider(PaymentProvider.TRIAL);

        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void verifyApplePurchase_shouldSucceed() {
        // Given
        ApplePurchaseRequest request = new ApplePurchaseRequest();
        request.setReceiptData("base64-receipt-data");

        doNothing().when(appleIAPService).validateAndProcessReceipt(any(User.class), anyString());
        when(subscriptionService.getTrialDaysRemaining(any(User.class))).thenReturn(0L);
        when(subscriptionService.canScan(any(User.class))).thenReturn(true);

        // When
        ResponseEntity<SuccessResponseEntity<SubscriptionStatusDto>> response = 
            subscriptionController.verifyApplePurchase(request, authentication);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        verify(appleIAPService).validateAndProcessReceipt(user, "base64-receipt-data");
    }

    @Test
    void verifyGooglePurchase_shouldSucceed() {
        // Given
        GooglePurchaseRequest request = new GooglePurchaseRequest();
        request.setPurchaseToken("purchase-token-123");
        request.setProductId("premium_monthly");

        doNothing().when(googlePlayBillingService).validateAndProcessPurchase(
            any(User.class), anyString(), anyString());
        when(subscriptionService.getTrialDaysRemaining(any(User.class))).thenReturn(0L);
        when(subscriptionService.canScan(any(User.class))).thenReturn(true);

        // When
        ResponseEntity<SuccessResponseEntity<SubscriptionStatusDto>> response = 
            subscriptionController.verifyGooglePurchase(request, authentication);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        verify(googlePlayBillingService).validateAndProcessPurchase(
            user, "purchase-token-123", "premium_monthly");
    }

    @Test
    void getStatus_shouldReturnSubscriptionStatus() {
        // Given
        doNothing().when(subscriptionService).checkTrialExpiration(any(User.class));
        when(subscriptionService.getTrialDaysRemaining(any(User.class))).thenReturn(2L);
        when(subscriptionService.canScan(any(User.class))).thenReturn(true);

        // When
        ResponseEntity<SuccessResponseEntity<SubscriptionStatusDto>> response = 
            subscriptionController.getStatus(authentication);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        SubscriptionStatusDto status = response.getBody().getData();
        assertEquals(SubscriptionPlan.TRIAL, status.getPlan());
        assertEquals(SubscriptionStatus.TRIAL, status.getStatus());
        assertEquals(2L, status.getTrialDaysRemaining());
        assertTrue(status.isCanScan());
        verify(subscriptionService).checkTrialExpiration(user);
    }

    @Test
    void canScan_shouldReturnCanScanStatus() {
        // Given
        doNothing().when(subscriptionService).checkTrialExpiration(any(User.class));
        when(subscriptionService.canScan(any(User.class))).thenReturn(true);
        when(subscriptionService.getTrialDaysRemaining(any(User.class))).thenReturn(2L);

        // When
        ResponseEntity<SuccessResponseEntity<Map<String, Object>>> response = 
            subscriptionController.canScan(authentication);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        Map<String, Object> data = response.getBody().getData();
        assertTrue((Boolean) data.get("canScan"));
        assertEquals(2L, data.get("trialDaysRemaining"));
        assertEquals(SubscriptionPlan.TRIAL, data.get("subscriptionPlan"));
        verify(subscriptionService).checkTrialExpiration(user);
    }

    @Test
    void canScan_shouldReturnFalse_whenTrialExpired() {
        // Given
        user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
        doNothing().when(subscriptionService).checkTrialExpiration(any(User.class));
        when(subscriptionService.canScan(any(User.class))).thenReturn(false);
        when(subscriptionService.getTrialDaysRemaining(any(User.class))).thenReturn(0L);

        // When
        ResponseEntity<SuccessResponseEntity<Map<String, Object>>> response = 
            subscriptionController.canScan(authentication);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> data = response.getBody().getData();
        assertFalse((Boolean) data.get("canScan"));
        assertEquals(0L, data.get("trialDaysRemaining"));
    }

    @Test
    void verifyApplePurchase_shouldThrowException_whenUserNotFound() {
        // Given
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.empty());
        ApplePurchaseRequest request = new ApplePurchaseRequest();
        request.setReceiptData("base64-receipt-data");

        // When/Then
        assertThrows(BadRequestException.class, () -> 
            subscriptionController.verifyApplePurchase(request, authentication));
    }

    @Test
    void verifyGooglePurchase_shouldThrowException_whenUserNotFound() {
        // Given
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.empty());
        GooglePurchaseRequest request = new GooglePurchaseRequest();
        request.setPurchaseToken("token");
        request.setProductId("premium_monthly");

        // When/Then
        assertThrows(BadRequestException.class, () -> 
            subscriptionController.verifyGooglePurchase(request, authentication));
    }
}

