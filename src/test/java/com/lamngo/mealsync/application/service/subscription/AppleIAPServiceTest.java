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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppleIAPServiceTest {

    @Mock
    private IUserRepo userRepo;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AppleIAPService appleIAPService;

    private User user;

    @BeforeEach
    void setUp() {
        // Set up service with reflection
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "test-secret");
        ReflectionTestUtils.setField(appleIAPService, "bundleId", "com.cookify.mealsync");

        // Use real User object for IAP tests
        user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setEmail("test@example.com");
    }

    @Test
    void validateAndProcessReceipt_shouldThrowException_whenSecretNotConfigured() {
        // Given
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "");
        String receiptData = "base64-receipt";

        // When/Then
        assertThrows(PaymentException.class, () -> 
            appleIAPService.validateAndProcessReceipt(user, receiptData));
    }

    @Test
    void validateAndProcessReceipt_shouldProcessValidReceipt() {
        // Given
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "test-secret");
        String receiptData = "base64-receipt";
        
        AppleIAPService.AppleReceiptResponse response = new AppleIAPService.AppleReceiptResponse();
        response.setStatus(0);
        
        AppleIAPService.LatestReceiptInfo receiptInfo = new AppleIAPService.LatestReceiptInfo();
        receiptInfo.setProductId("com.cookify.premium.monthly");
        receiptInfo.setTransactionId("transaction-123");
        receiptInfo.setPurchaseDateMs(System.currentTimeMillis() - 86400000); // 1 day ago
        receiptInfo.setExpiresDateMs(System.currentTimeMillis() + 2592000000L); // 30 days from now
        response.setLatestReceiptInfo(List.of(receiptInfo));
        response.setLatestReceipt("latest-receipt-data");

        org.springframework.http.ResponseEntity<AppleIAPService.AppleReceiptResponse> responseEntity = 
            org.springframework.http.ResponseEntity.ok(response);

        // Mock RestTemplate using reflection
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(appleIAPService, "restTemplate", mockRestTemplate);
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(AppleIAPService.AppleReceiptResponse.class)))
            .thenReturn(responseEntity);
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        appleIAPService.validateAndProcessReceipt(user, receiptData);

        // Then
        assertEquals(SubscriptionPlan.PREMIUM_MONTHLY, user.getSubscriptionPlan());
        assertEquals(SubscriptionStatus.ACTIVE, user.getSubscriptionStatus());
        assertEquals("apple", user.getPaymentProvider().name());
        assertEquals("transaction-123", user.getPaymentProviderId());
        verify(userRepo).save(user);
    }

    @Test
    void validateAndProcessReceipt_shouldTrySandbox_whenProductionFails() {
        // Given
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "test-secret");
        String receiptData = "base64-receipt";
        
        // Production response with sandbox status
        AppleIAPService.AppleReceiptResponse productionResponse = new AppleIAPService.AppleReceiptResponse();
        productionResponse.setStatus(21007); // Sandbox receipt
        
        // Sandbox response
        AppleIAPService.AppleReceiptResponse sandboxResponse = new AppleIAPService.AppleReceiptResponse();
        sandboxResponse.setStatus(0);
        
        AppleIAPService.LatestReceiptInfo receiptInfo = new AppleIAPService.LatestReceiptInfo();
        receiptInfo.setProductId("com.cookify.premium.yearly");
        receiptInfo.setTransactionId("transaction-456");
        receiptInfo.setPurchaseDateMs(System.currentTimeMillis());
        receiptInfo.setExpiresDateMs(System.currentTimeMillis() + 31536000000L); // 1 year from now
        sandboxResponse.setLatestReceiptInfo(List.of(receiptInfo));

        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(appleIAPService, "restTemplate", mockRestTemplate);
        when(mockRestTemplate.postForEntity(contains("buy.itunes.apple.com"), any(), any()))
            .thenReturn(org.springframework.http.ResponseEntity.ok(productionResponse));
        when(mockRestTemplate.postForEntity(contains("sandbox.itunes.apple.com"), any(), any()))
            .thenReturn(org.springframework.http.ResponseEntity.ok(sandboxResponse));
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        appleIAPService.validateAndProcessReceipt(user, receiptData);

        // Then
        assertEquals(SubscriptionPlan.PREMIUM_YEARLY, user.getSubscriptionPlan());
        verify(mockRestTemplate).postForEntity(contains("sandbox.itunes.apple.com"), any(), any());
    }

    @Test
    void validateAndProcessReceipt_shouldThrowException_whenInvalidReceipt() {
        // Given
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "test-secret");
        String receiptData = "base64-receipt";
        
        AppleIAPService.AppleReceiptResponse response = new AppleIAPService.AppleReceiptResponse();
        response.setStatus(21002); // Invalid receipt

        RestTemplate mockRestTemplate = (RestTemplate) ReflectionTestUtils.getField(appleIAPService, "restTemplate");
        when(mockRestTemplate.postForEntity(anyString(), any(), any()))
            .thenReturn(org.springframework.http.ResponseEntity.ok(response));

        // When/Then
        assertThrows(PaymentException.class, () -> 
            appleIAPService.validateAndProcessReceipt(user, receiptData));
    }

    @Test
    void validateAndProcessReceipt_shouldThrowException_whenUnknownProductId() {
        // Given
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "test-secret");
        String receiptData = "base64-receipt";
        
        AppleIAPService.AppleReceiptResponse response = new AppleIAPService.AppleReceiptResponse();
        response.setStatus(0);
        
        AppleIAPService.LatestReceiptInfo receiptInfo = new AppleIAPService.LatestReceiptInfo();
        receiptInfo.setProductId("unknown.product.id");
        receiptInfo.setTransactionId("transaction-123");
        receiptInfo.setPurchaseDateMs(System.currentTimeMillis());
        receiptInfo.setExpiresDateMs(System.currentTimeMillis() + 2592000000L);
        response.setLatestReceiptInfo(List.of(receiptInfo));

        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(appleIAPService, "restTemplate", mockRestTemplate);
        when(mockRestTemplate.postForEntity(anyString(), any(), any()))
            .thenReturn(org.springframework.http.ResponseEntity.ok(response));

        // When/Then
        assertThrows(BadRequestException.class, () -> 
            appleIAPService.validateAndProcessReceipt(user, receiptData));
    }

    @Test
    void validateAndProcessReceipt_shouldSetExpired_whenSubscriptionExpired() {
        // Given
        ReflectionTestUtils.setField(appleIAPService, "appSharedSecret", "test-secret");
        String receiptData = "base64-receipt";
        
        AppleIAPService.AppleReceiptResponse response = new AppleIAPService.AppleReceiptResponse();
        response.setStatus(0);
        
        AppleIAPService.LatestReceiptInfo receiptInfo = new AppleIAPService.LatestReceiptInfo();
        receiptInfo.setProductId("com.cookify.premium.monthly");
        receiptInfo.setTransactionId("transaction-123");
        receiptInfo.setPurchaseDateMs(System.currentTimeMillis() - 2592000000L); // 30 days ago
        receiptInfo.setExpiresDateMs(System.currentTimeMillis() - 86400000); // Expired 1 day ago
        response.setLatestReceiptInfo(List.of(receiptInfo));

        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(appleIAPService, "restTemplate", mockRestTemplate);
        when(mockRestTemplate.postForEntity(anyString(), any(), any()))
            .thenReturn(org.springframework.http.ResponseEntity.ok(response));
        when(userRepo.save(any(User.class))).thenReturn(user);

        // When
        appleIAPService.validateAndProcessReceipt(user, receiptData);

        // Then
        assertEquals(SubscriptionStatus.EXPIRED, user.getSubscriptionStatus());
        verify(userRepo).save(user);
    }
}

