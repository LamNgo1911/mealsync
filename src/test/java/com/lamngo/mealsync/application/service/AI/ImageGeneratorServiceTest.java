package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.ImageGeneratorServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageGeneratorServiceTest {
    private GeminiImageApiClient service;
    private PromptLoader promptLoader;

    @BeforeEach
    void setUp() {
        promptLoader = mock(PromptLoader.class);
        service = new GeminiImageApiClient(promptLoader);
        // Set required fields using ReflectionTestUtils
        ReflectionTestUtils.setField(service, "geminiApiKey", "dummy");
        ReflectionTestUtils.setField(service, "geminiApiBaseUrl", "http://dummy.url");
        ReflectionTestUtils.setField(service, "baseUrl", "http://dummy.url");
        ReflectionTestUtils.setField(service, "apiKey", "dummy");
        
        // Mock prompt loader to return a simple prompt
        when(promptLoader.loadAndFormatPrompt(anyString(), any(Map.class)))
                .thenReturn("Ultra-realistic, high-resolution DSLR food photo");
    }

    @Test
    void callGeminiAPI_returnsBase64Image() {
        // Use spy to mock the actual HTTP call
        GeminiImageApiClient spyService = spy(service);
        String fakeResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"ZmFrZS1pbWFnZQ==\"}}]}}]}";
        
        // Mock the private callGeminiAPI method using doReturn
        doReturn("ZmFrZS1pbWFnZQ==").when(spyService).callGeminiAPI(anyString());
        
        // Act - test through public method
        String result = spyService.generateImage("cat", List.of("egg"), "desc");
        
        // Assert
        assertEquals("ZmFrZS1pbWFnZQ==", result);
    }

    @Test
    void callGeminiAPI_handlesError() {
        // Use spy to mock error scenario
        GeminiImageApiClient spyService = spy(service);
        doThrow(new ImageGeneratorServiceException("API error")).when(spyService).callGeminiAPI(anyString());

        assertThrows(ImageGeneratorServiceException.class, () ->
            spyService.generateImage("dog", List.of("egg"), "desc")
        );
    }

    @Test
    void generateImage_returnsBase64() {
        GeminiImageApiClient spyService = spy(service);
        doReturn("ZmFrZS1pbWFnZQ==").when(spyService).callGeminiAPI(anyString());
        String result = spyService.generateImage("prompt", List.of("egg"), "desc");
        assertEquals("ZmFrZS1pbWFnZQ==", result);
    }

    @Test
    void generateImage_handlesException() {
        GeminiImageApiClient spyService = spy(service);
        doThrow(new ImageGeneratorServiceException("fail")).when(spyService).callGeminiAPI(anyString());
        assertThrows(ImageGeneratorServiceException.class, () -> spyService.generateImage("prompt", List.of("egg"), "desc"));
    }
}
