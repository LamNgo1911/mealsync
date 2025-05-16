package com.lamngo.mealsync.presentation.error;

public class IngredientRecognitionServiceException extends RuntimeException {
    public IngredientRecognitionServiceException(String message) {
        super(message);
    }
    public IngredientRecognitionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
