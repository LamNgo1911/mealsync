package com.lamngo.mealsync.application.dto.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusDto {
    private SubscriptionPlan plan;
    private SubscriptionStatus status;
    private long trialDaysRemaining;
    private boolean canScan;
    private Instant subscriptionEndDate;
    private Integer scansUsed;
    private Integer scansLimit;
    private String paymentProvider;
}

