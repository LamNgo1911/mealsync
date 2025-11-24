package com.lamngo.mealsync.application.service.subscription;

import com.lamngo.mealsync.domain.model.user.SubscriptionPlan;
import com.lamngo.mealsync.domain.model.user.SubscriptionStatus;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceUnitTest {

    @Mock
    private IUserRepo userRepo;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void canScan_trialUser_nullTrialEndDate_shouldReturnFalse() {
        User user = new User();
        user.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        user.setTrialEndDate(null); // Simulate the issue

        // Should return false safely
        assertFalse(subscriptionService.canScan(user));
    }

    @Test
    void checkTrialExpiration_trialUser_nullTrialEndDate_shouldNotThrow() {
        User user = new User();
        user.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        user.setTrialEndDate(null); // Simulate the issue

        // Should not throw exception
        assertDoesNotThrow(() -> subscriptionService.checkTrialExpiration(user));
    }
}
