package com.lamngo.mealsync.application.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GooglePurchaseRequest {
    @NotBlank(message = "Purchase token is required")
    private String purchaseToken;
    
    @NotBlank(message = "Product ID is required")
    private String productId; // "premium_monthly" or "premium_yearly"
}

