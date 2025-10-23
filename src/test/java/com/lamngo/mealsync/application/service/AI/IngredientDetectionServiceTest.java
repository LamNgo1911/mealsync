package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.AIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngredientDetectionServiceTest {

    @InjectMocks
    private IngredientDetectionService ingredientDetectionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up required configuration values for testing
        ReflectionTestUtils.setField(ingredientDetectionService, "openAIApiBaseUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(ingredientDetectionService, "openAIApiKey", "test-api-key");
    }

    @Test
    void detectRawIngredients_nullImage_throwsAIServiceException() {
        AIServiceException exception = assertThrows(AIServiceException.class,
            () -> ingredientDetectionService.detectRawIngredients(null));

        assertEquals("Image file is empty or null", exception.getMessage());
    }

    @Test
    void detectRawIngredients_emptyImage_throwsAIServiceException() {
        MultipartFile emptyImage = mock(MultipartFile.class);
        when(emptyImage.isEmpty()).thenReturn(true);

        AIServiceException exception = assertThrows(AIServiceException.class,
            () -> ingredientDetectionService.detectRawIngredients(emptyImage));

        assertEquals("Image file is empty or null", exception.getMessage());
    }

    @Test
    void detectRawIngredients_invalidApiConfiguration_throwsAIServiceException() {
        // Set invalid configuration
        ReflectionTestUtils.setField(ingredientDetectionService, "openAIApiBaseUrl", null);

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getOriginalFilename()).thenReturn("test.jpg");

        AIServiceException exception = assertThrows(AIServiceException.class,
            () -> ingredientDetectionService.detectRawIngredients(image));

        assertEquals("OpenAI API configuration error", exception.getMessage());
    }

    @Test
    void detectRawIngredients_emptyApiKey_throwsAIServiceException() {
        // Set empty API key
        ReflectionTestUtils.setField(ingredientDetectionService, "openAIApiKey", "");

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getOriginalFilename()).thenReturn("test.jpg");

        AIServiceException exception = assertThrows(AIServiceException.class,
            () -> ingredientDetectionService.detectRawIngredients(image));

        assertEquals("OpenAI API configuration error", exception.getMessage());
    }

    @Test
    void detectRawIngredients_invalidUrlProtocol_throwsAIServiceException() {
        // Set invalid URL (not https)
        ReflectionTestUtils.setField(ingredientDetectionService, "openAIApiBaseUrl", "http://api.openai.com");

        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getOriginalFilename()).thenReturn("test.jpg");

        AIServiceException exception = assertThrows(AIServiceException.class,
            () -> ingredientDetectionService.detectRawIngredients(image));

        assertEquals("OpenAI API configuration error", exception.getMessage());
    }
}
