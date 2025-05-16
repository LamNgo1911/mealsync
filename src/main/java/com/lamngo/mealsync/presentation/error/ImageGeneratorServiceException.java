package com.lamngo.mealsync.presentation.error;

public class ImageGeneratorServiceException extends RuntimeException {
    public ImageGeneratorServiceException(String message) {
        super(message);
    }
    public ImageGeneratorServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
