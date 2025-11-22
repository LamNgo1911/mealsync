package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.subscription.ApplePurchaseRequest;
import com.lamngo.mealsync.application.dto.subscription.GooglePurchaseRequest;
import com.lamngo.mealsync.application.dto.subscription.SubscriptionStatusDto;
import com.lamngo.mealsync.application.service.subscription.AppleIAPService;
import com.lamngo.mealsync.application.service.subscription.GooglePlayBillingService;
import com.lamngo.mealsync.application.service.subscription.SubscriptionService;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {
    
    private final AppleIAPService appleIAPService;
    private final GooglePlayBillingService googlePlayBillingService;
    private final SubscriptionService subscriptionService;
    private final IUserRepo userRepo;
    
    public SubscriptionController(
            AppleIAPService appleIAPService,
            GooglePlayBillingService googlePlayBillingService,
            SubscriptionService subscriptionService,
            IUserRepo userRepo) {
        this.appleIAPService = appleIAPService;
        this.googlePlayBillingService = googlePlayBillingService;
        this.subscriptionService = subscriptionService;
        this.userRepo = userRepo;
    }
    
    /**
     * Verify Apple IAP purchase
     */
    @PostMapping("/apple/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<SubscriptionStatusDto>> verifyApplePurchase(
            @Valid @RequestBody ApplePurchaseRequest request,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        appleIAPService.validateAndProcessReceipt(user, request.getReceiptData());
        
        SubscriptionStatusDto status = buildSubscriptionStatus(user);
        SuccessResponseEntity<SubscriptionStatusDto> response = new SuccessResponseEntity<>();
        response.setData(status);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Verify Google Play purchase
     */
    @PostMapping("/google/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<SubscriptionStatusDto>> verifyGooglePurchase(
            @Valid @RequestBody GooglePurchaseRequest request,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        googlePlayBillingService.validateAndProcessPurchase(
            user, 
            request.getPurchaseToken(), 
            request.getProductId());
        
        SubscriptionStatusDto status = buildSubscriptionStatus(user);
        SuccessResponseEntity<SubscriptionStatusDto> response = new SuccessResponseEntity<>();
        response.setData(status);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get subscription status
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<SubscriptionStatusDto>> getStatus(Authentication auth) {
        User user = getCurrentUser(auth);
        subscriptionService.checkTrialExpiration(user);
        
        SubscriptionStatusDto status = buildSubscriptionStatus(user);
        SuccessResponseEntity<SubscriptionStatusDto> response = new SuccessResponseEntity<>();
        response.setData(status);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Check if user can scan
     */
    @GetMapping("/can-scan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<Map<String, Object>>> canScan(Authentication auth) {
        User user = getCurrentUser(auth);
        subscriptionService.checkTrialExpiration(user);
        
        boolean canScan = subscriptionService.canScan(user);
        long trialDaysRemaining = subscriptionService.getTrialDaysRemaining(user);
        
        Map<String, Object> data = new HashMap<>();
        data.put("canScan", canScan);
        data.put("trialDaysRemaining", trialDaysRemaining);
        data.put("subscriptionPlan", user.getSubscriptionPlan());
        data.put("subscriptionStatus", user.getSubscriptionStatus());
        data.put("scansUsed", user.getScansUsed());
        data.put("scansLimit", user.getScansLimit());
        
        SuccessResponseEntity<Map<String, Object>> response = new SuccessResponseEntity<>();
        response.setData(data);
        
        return ResponseEntity.ok(response);
    }
    
    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepo.findByEmail(email)
            .orElseThrow(() -> new BadRequestException("User not found"));
    }
    
    private SubscriptionStatusDto buildSubscriptionStatus(User user) {
        return SubscriptionStatusDto.builder()
            .plan(user.getSubscriptionPlan())
            .status(user.getSubscriptionStatus())
            .trialDaysRemaining(subscriptionService.getTrialDaysRemaining(user))
            .canScan(subscriptionService.canScan(user))
            .subscriptionEndDate(user.getSubscriptionEndDate())
            .scansUsed(user.getScansUsed())
            .scansLimit(user.getScansLimit())
            .paymentProvider(user.getPaymentProvider() != null ? user.getPaymentProvider().name() : null)
            .build();
    }
}

