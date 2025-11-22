package com.lamngo.mealsync.application.service.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SubscriptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);
    private static final int TRIAL_DAYS = 3;
    
    private final IUserRepo userRepo;
    
    public SubscriptionService(IUserRepo userRepo) {
        this.userRepo = userRepo;
    }
    
    /**
     * Initialize 3-day free trial for new user
     */
    @Transactional
    public void initializeTrial(User user) {
        Instant now = Instant.now();
        user.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        user.setAccountCreatedAt(now);
        user.setTrialEndDate(now.plus(TRIAL_DAYS, ChronoUnit.DAYS));
        user.setScansLimit(999); // Unlimited during trial
        user.setScansUsed(0);
        user.setPaymentProvider(com.lamngo.mealsync.domain.model.user.PaymentProvider.TRIAL);
        userRepo.save(user);
        logger.info("Initialized 3-day trial for user: {}", user.getEmail());
    }
    
    /**
     * Check if user can scan (trial or active subscription)
     * Automatically updates trial expiration status if needed
     */
    public boolean canScan(User user) {
        // Check and update trial expiration first
        checkTrialExpiration(user);
        
        // Premium users with active subscription
        if (user.getSubscriptionPlan() == SubscriptionPlan.PREMIUM_MONTHLY || 
            user.getSubscriptionPlan() == SubscriptionPlan.PREMIUM_YEARLY) {
            boolean isActive = user.getSubscriptionStatus() == SubscriptionStatus.ACTIVE;
            boolean notExpired = user.getSubscriptionEndDate() == null || 
                               Instant.now().isBefore(user.getSubscriptionEndDate());
            return isActive && notExpired;
        }
        
        // Trial users
        if (user.getSubscriptionPlan() == SubscriptionPlan.TRIAL) {
            return Instant.now().isBefore(user.getTrialEndDate());
        }
        
        return false;
    }
    
    /**
     * Get days remaining in trial
     */
    public long getTrialDaysRemaining(User user) {
        if (user.getSubscriptionPlan() != SubscriptionPlan.TRIAL) {
            return 0;
        }
        
        Instant now = Instant.now();
        if (user.getTrialEndDate() == null || now.isAfter(user.getTrialEndDate())) {
            return 0;
        }
        
        return ChronoUnit.DAYS.between(now, user.getTrialEndDate());
    }
    
    /**
     * Increment scan usage (only for trial users)
     */
    @Transactional
    public void incrementScanUsage(User user) {
        if (user.getSubscriptionPlan() == SubscriptionPlan.TRIAL) {
            user.setScansUsed(user.getScansUsed() + 1);
            userRepo.save(user);
        }
        // Premium users have unlimited scans, no need to track
    }
    
    /**
     * Check and update trial expiration status
     */
    @Transactional
    public void checkTrialExpiration(User user) {
        if (user.getSubscriptionPlan() == SubscriptionPlan.TRIAL && 
            user.getSubscriptionStatus() == SubscriptionStatus.TRIAL) {
            if (Instant.now().isAfter(user.getTrialEndDate())) {
                user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                userRepo.save(user);
                logger.info("Trial expired for user: {}", user.getEmail());
            }
        }
    }
    
    /**
     * Scheduled job to automatically expire trials daily at midnight UTC
     * This ensures trials are marked as expired even if users don't call the API
     */
    @Scheduled(cron = "0 0 * * * ?") // Run daily at midnight UTC
    @Transactional
    public void expireTrialsDaily() {
        logger.info("Running daily trial expiration check");
        Instant now = Instant.now();
        List<User> allUsers = userRepo.findAll();
        
        int expiredCount = 0;
        for (User user : allUsers) {
            if (user.getSubscriptionPlan() == SubscriptionPlan.TRIAL && 
                user.getSubscriptionStatus() == SubscriptionStatus.TRIAL &&
                user.getTrialEndDate() != null &&
                now.isAfter(user.getTrialEndDate())) {
                user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                userRepo.save(user);
                expiredCount++;
            }
        }
        
        if (expiredCount > 0) {
            logger.info("Expired {} trial subscriptions", expiredCount);
        } else {
            logger.debug("No trials expired today");
        }
    }
}

