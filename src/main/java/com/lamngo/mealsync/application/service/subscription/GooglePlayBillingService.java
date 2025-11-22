package com.lamngo.mealsync.application.service.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.PaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for validating Google Play Billing purchases.
 * 
 * Note: To use this service, you need to:
 * 1. Add Google Play Billing API dependency to pom.xml
 * 2. Create a service account in Google Cloud Console
 * 3. Download the service account key JSON file
 * 4. Set google.service-account-key-path in env.properties
 * 
 * For now, this is a placeholder that accepts purchases without validation.
 * You should implement proper validation using Google Play Developer API.
 */
@Service
public class GooglePlayBillingService {
    
    private static final Logger logger = LoggerFactory.getLogger(GooglePlayBillingService.class);
    
    @Value("${google.package-name:com.cookify.mealsync}")
    private String packageName;
    
    @Value("${google.service-account-key-path:}")
    private String serviceAccountKeyPath;
    
    private final IUserRepo userRepo;
    
    public GooglePlayBillingService(IUserRepo userRepo) {
        this.userRepo = userRepo;
    }
    
    /**
     * Validates Google Play purchase and processes subscription.
     * 
     * TODO: Implement proper validation using Google Play Developer API
     * For now, this is a simplified version that accepts the purchase.
     * In production, you should validate the purchase token with Google's API.
     */
    @Transactional
    public void validateAndProcessPurchase(User user, String purchaseToken, String productId) {
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty()) {
            logger.warn("Google Play service account key not configured. Purchase validation skipped.");
            logger.warn("For production, set google.service-account-key-path in env.properties");
        }
        
        try {
            // Map Google product ID to subscription plan
            SubscriptionPlan plan = mapProductIdToPlan(productId);
            
            // TODO: Validate purchase token with Google Play Developer API
            // For now, we'll accept it and set expiration based on plan
            // In production, you should:
            // 1. Use Google Play Developer API to validate the purchase token
            // 2. Get actual expiration date from Google's response
            // 3. Check payment state (0 = pending, 1 = purchased, 2 = free trial)
            
            Instant now = Instant.now();
            Instant endDate = plan == SubscriptionPlan.PREMIUM_MONTHLY 
                ? now.plus(30, java.time.temporal.ChronoUnit.DAYS)
                : now.plus(365, java.time.temporal.ChronoUnit.DAYS);
            
            user.setSubscriptionPlan(plan);
            user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            user.setPaymentProvider(com.lamngo.mealsync.domain.model.user.PaymentProvider.GOOGLE_PLAY);
            user.setPaymentProviderId(purchaseToken);
            user.setSubscriptionStartDate(now);
            user.setSubscriptionEndDate(endDate);
            user.setScansLimit(plan.getScansLimit());
            
            userRepo.save(user);
            
            logger.info("Processed Google Play subscription for user: {}, plan: {}, productId: {}", 
                user.getEmail(), plan, productId);
            
            // TODO: Log warning in production if validation is not implemented
            if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty()) {
                logger.warn("Google Play purchase accepted without validation. Implement proper validation for production!");
            }
            
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to process Google Play purchase for user: {}", user.getEmail(), e);
            throw new PaymentException("Failed to process Google Play purchase: " + e.getMessage());
        }
    }
    
    private SubscriptionPlan mapProductIdToPlan(String productId) {
        // These match your Google Play Console product IDs
        return switch (productId) {
            case "premium_monthly" -> SubscriptionPlan.PREMIUM_MONTHLY;
            case "premium_yearly" -> SubscriptionPlan.PREMIUM_YEARLY;
            default -> throw new BadRequestException("Unknown Google product ID: " + productId);
        };
    }
}

