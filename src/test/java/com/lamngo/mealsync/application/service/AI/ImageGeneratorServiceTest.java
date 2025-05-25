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

    @BeforeEach
    void setUp() {
        service = new ImageGeneratorService();
        webClient = mock(WebClient.class);
        ReflectionTestUtils.setField(service, "webClient", webClient);
        ReflectionTestUtils.setField(service, "stabilityApiKey", "dummy");
        ReflectionTestUtils.setField(service, "stabilityApiUrl", "http://dummy.url");
    }

    @Test
    void callStabilityAiAPI_returnsBase64Image() {
        // Arrange
        String prompt = "cat";
        String fakeResponse = "{\"artifacts\":[{\"base64\":\"ZmFrZS1pbWFnZQ==\"}]}";
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        doReturn(bodySpec).when(postSpec).bodyValue(any(Map.class));
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(fakeResponse));
        // Act
        String result = ReflectionTestUtils.invokeMethod(service, "callStabilityAiAPI", prompt);
        // Assert
        assertEquals("ZmFrZS1pbWFnZQ==", result);
    }

    @Test
    void callStabilityAiAPI_handlesError() {
        String prompt = "dog";
        WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        doReturn(bodySpec).when(postSpec).bodyValue(any(Map.class));
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());

        assertThrows(ImageGeneratorServiceException.class, () ->
            ReflectionTestUtils.invokeMethod(service, "callStabilityAiAPI", prompt)
        );
    }

    @Test
    void generateImage_returnsBase64() {
        ImageGeneratorService spyService = spy(service);
        doReturn("ZmFrZS1pbWFnZQ==").when(spyService).callStabilityAiAPI(anyString());
        String result = spyService.generateImage("prompt", List.of("egg"), "desc");
        assertEquals("ZmFrZS1pbWFnZQ==", result);
    }

    @Test
    void generateImage_handlesException() {
        ImageGeneratorService spyService = spy(service);
        doThrow(new ImageGeneratorServiceException("fail")).when(spyService).callStabilityAiAPI(anyString());
        assertThrows(ImageGeneratorServiceException.class, () -> spyService.generateImage("prompt", List.of("egg"), "desc"));
    }
}
