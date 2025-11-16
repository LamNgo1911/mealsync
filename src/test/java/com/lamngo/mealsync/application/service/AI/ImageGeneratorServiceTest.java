package com.lamngo.mealsync.application.service.AI;

import com.lamngo.mealsync.presentation.error.ImageGeneratorServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageGeneratorServiceTest {
    private ImageGeneratorService service;
    private WebClient webClient;
    private PromptLoader promptLoader;

    @BeforeEach
    void setUp() {
        promptLoader = mock(PromptLoader.class);
        service = new ImageGeneratorService(promptLoader);
        webClient = mock(WebClient.class);
        ReflectionTestUtils.setField(service, "webClient", webClient);
        ReflectionTestUtils.setField(service, "geminiApiKey", "dummy");
        ReflectionTestUtils.setField(service, "geminiApiBaseUrl", "http://dummy.url");
        
        // Mock prompt loader to return a simple prompt
        when(promptLoader.loadAndFormatPrompt(anyString(), any(Map.class)))
                .thenReturn("Ultra-realistic, high-resolution DSLR food photo");
    }

    @Test
    void callGeminiAPI_returnsBase64Image() {
        // Arrange
        String prompt = "cat";
        String fakeResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"ZmFrZS1pbWFnZQ==\"}}]}}]}";
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        doReturn(bodySpec).when(postSpec).uri(anyString());
        doReturn(bodySpec).when(bodySpec).contentType(any(org.springframework.http.MediaType.class));
        doReturn(bodySpec).when(bodySpec).bodyValue(anyString());
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(fakeResponse));
        // Act
        String result = ReflectionTestUtils.invokeMethod(service, "callGeminiAPI", prompt);
        // Assert
        assertEquals("ZmFrZS1pbWFnZQ==", result);
    }

    @Test
    void callGeminiAPI_handlesError() {
        String prompt = "dog";
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        doReturn(bodySpec).when(postSpec).uri(anyString());
        doReturn(bodySpec).when(bodySpec).contentType(any(org.springframework.http.MediaType.class));
        doReturn(bodySpec).when(bodySpec).bodyValue(anyString());
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());

        assertThrows(ImageGeneratorServiceException.class, () ->
            ReflectionTestUtils.invokeMethod(service, "callGeminiAPI", prompt)
        );
    }

    @Test
    void generateImage_returnsBase64() {
        ImageGeneratorService spyService = spy(service);
        doReturn("ZmFrZS1pbWFnZQ==").when(spyService).callGeminiAPI(anyString());
        String result = spyService.generateImage("prompt", List.of("egg"), "desc");
        assertEquals("ZmFrZS1pbWFnZQ==", result);
    }

    @Test
    void generateImage_handlesException() {
        ImageGeneratorService spyService = spy(service);
        doThrow(new ImageGeneratorServiceException("fail")).when(spyService).callGeminiAPI(anyString());
        assertThrows(ImageGeneratorServiceException.class, () -> spyService.generateImage("prompt", List.of("egg"), "desc"));
    }
}
