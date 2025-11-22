package com.lamngo.mealsync.domain.model.user;

import lombok.Getter;

@Getter
public enum SubscriptionPlan {
    TRIAL(999, true, true),           // 3 days free, unlimited scans
    PREMIUM_MONTHLY(999, true, true),  // $4.99/month, unlimited
    PREMIUM_YEARLY(999, true, true);  // $39.99/year, unlimited
    
    private final int scansLimit; // 999 = unlimited
    private final boolean includesImages;
    private final boolean includesMealPlans;
    
    SubscriptionPlan(int scansLimit, boolean includesImages, boolean includesMealPlans) {
        this.scansLimit = scansLimit;
        this.includesImages = includesImages;
        this.includesMealPlans = includesMealPlans;
    }
    
    /**
     * Get price in cents (for reference, actual pricing set in App Store/Play Store)
     */
    public int getPriceInCents() {
        return switch (this) {
            case PREMIUM_MONTHLY -> 499;  // $4.99
            case PREMIUM_YEARLY -> 3999;  // $39.99
            case TRIAL -> 0;
        };
    }
    
    /**
     * Get billing interval in months
     */
    public int getBillingIntervalMonths() {
        return switch (this) {
            case PREMIUM_MONTHLY -> 1;
            case PREMIUM_YEARLY -> 12;
            case TRIAL -> 0;
        };
    }
}

