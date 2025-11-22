package com.lamngo.mealsync.application.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplePurchaseRequest {
    @NotBlank(message = "Receipt data is required")
    private String receiptData; // Base64 encoded receipt
}

