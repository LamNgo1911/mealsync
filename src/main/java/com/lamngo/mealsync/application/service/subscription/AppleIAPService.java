package com.lamngo.mealsync.application.service.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.PaymentException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AppleIAPService {
    
    private static final Logger logger = LoggerFactory.getLogger(AppleIAPService.class);
    
    @Value("${apple.app-shared-secret:}")
    private String appSharedSecret;
    
    @Value("${apple.bundle-id:com.cookify.mealsync}")
    private String bundleId;
    
    private final RestTemplate restTemplate;
    private final IUserRepo userRepo;
    
    public AppleIAPService(IUserRepo userRepo) {
        this.restTemplate = new RestTemplate();
        this.userRepo = userRepo;
    }
    
    /**
     * Validates Apple receipt and processes subscription
     */
    @Transactional
    public void validateAndProcessReceipt(User user, String receiptData) {
        if (appSharedSecret == null || appSharedSecret.isEmpty()) {
            throw new PaymentException("Apple IAP not configured. Please set apple.app-shared-secret in env.properties");
        }
        
        try {
            // Try production first
            AppleReceiptResponse response = validateReceipt(receiptData, false);
            
            // If sandbox receipt (status 21007), try sandbox
            if (response.getStatus() == 21007) {
                logger.info("Production receipt failed, trying sandbox for user: {}", user.getEmail());
                response = validateReceipt(receiptData, true);
            }
            
            if (response.getStatus() != 0) {
                throw new PaymentException("Invalid receipt. Status: " + response.getStatus());
            }
            
            // Process the receipt
            processReceiptResponse(user, response);
            
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to validate Apple receipt for user: {}", user.getEmail(), e);
            throw new PaymentException("Failed to validate Apple receipt: " + e.getMessage());
        }
    }
    
    private AppleReceiptResponse validateReceipt(String receiptData, boolean sandbox) {
        String url = sandbox 
            ? "https://sandbox.itunes.apple.com/verifyReceipt"
            : "https://buy.itunes.apple.com/verifyReceipt";
        
        Map<String, Object> request = new HashMap<>();
        request.put("receipt-data", receiptData);
        request.put("password", appSharedSecret);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<AppleReceiptResponse> response = restTemplate.postForEntity(
                url, entity, AppleReceiptResponse.class);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error calling Apple verifyReceipt API", e);
            throw new PaymentException("Failed to connect to Apple servers: " + e.getMessage());
        }
    }
    
    private void processReceiptResponse(User user, AppleReceiptResponse response) {
        Receipt receipt = response.getReceipt();
        List<LatestReceiptInfo> latestReceipts = response.getLatestReceiptInfo();
        
        if (latestReceipts == null || latestReceipts.isEmpty()) {
            throw new PaymentException("No subscription found in receipt");
        }
        
        // Get the latest subscription
        LatestReceiptInfo latestReceipt = latestReceipts.get(0);
        String productId = latestReceipt.getProductId();
        
        // Map Apple product ID to subscription plan
        SubscriptionPlan plan = mapProductIdToPlan(productId);
        
        // Check if subscription is active
        long expiresDateMs = latestReceipt.getExpiresDateMs();
        boolean isActive = expiresDateMs > System.currentTimeMillis();
        
        user.setSubscriptionPlan(plan);
        user.setSubscriptionStatus(isActive ? SubscriptionStatus.ACTIVE : SubscriptionStatus.EXPIRED);
        user.setPaymentProvider(com.lamngo.mealsync.domain.model.user.PaymentProvider.APPLE_IAP);
        user.setPaymentProviderId(latestReceipt.getTransactionId());
        user.setReceiptData(response.getLatestReceipt() != null ? response.getLatestReceipt() : "");
        user.setSubscriptionStartDate(Instant.ofEpochMilli(latestReceipt.getPurchaseDateMs()));
        user.setSubscriptionEndDate(Instant.ofEpochMilli(expiresDateMs));
        user.setScansLimit(plan.getScansLimit());
        
        userRepo.save(user);
        
        logger.info("Processed Apple subscription for user: {}, plan: {}, active: {}", 
            user.getEmail(), plan, isActive);
    }
    
    private SubscriptionPlan mapProductIdToPlan(String productId) {
        // These match your App Store Connect product IDs
        return switch (productId) {
            case "com.cookify.premium.monthly" -> SubscriptionPlan.PREMIUM_MONTHLY;
            case "com.cookify.premium.yearly" -> SubscriptionPlan.PREMIUM_YEARLY;
            default -> throw new BadRequestException("Unknown Apple product ID: " + productId);
        };
    }
    
    // DTOs for Apple API response
    @Data
    public static class AppleReceiptResponse {
        private int status;
        private Receipt receipt;
        @JsonProperty("latest_receipt")
        private String latestReceipt;
        @JsonProperty("latest_receipt_info")
        private List<LatestReceiptInfo> latestReceiptInfo;
    }
    
    @Data
    public static class Receipt {
        @JsonProperty("bundle_id")
        private String bundleId;
    }
    
    @Data
    public static class LatestReceiptInfo {
        @JsonProperty("product_id")
        private String productId;
        @JsonProperty("transaction_id")
        private String transactionId;
        @JsonProperty("purchase_date_ms")
        private Long purchaseDateMs;
        @JsonProperty("expires_date_ms")
        private Long expiresDateMs;
    }
}

