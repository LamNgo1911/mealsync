package com.lamngo.mealsync.presentation.error;

public class GeminiServiceException extends RuntimeException {
    public GeminiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    public GeminiServiceException(String message) {
        super(message);
    }
}
