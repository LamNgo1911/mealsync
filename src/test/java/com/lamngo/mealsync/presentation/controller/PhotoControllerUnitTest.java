package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.PhotoRecipeRequest;
import com.lamngo.mealsync.application.service.AI.GeminiImageApiClient;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoControllerUnitTest {
    @Mock GeminiImageApiClient geminiImageApiClient;
    @Mock S3Service s3UploadService;
    @InjectMocks PhotoController controller;

    @BeforeEach
    void setup() { MockitoAnnotations.openMocks(this); }

    @Test
    void generateAndUploadImage_success() {
        PhotoRecipeRequest request = new PhotoRecipeRequest();
        request.setRecipeName("Omelette");
        request.setIngredients(List.of("egg", "milk"));
        request.setDescription("A fluffy omelette");
        String fakeBase64 = java.util.Base64.getEncoder().encodeToString("imagebytes".getBytes());
        String fakeUrl = "http://example.com/image.png";
        when(geminiImageApiClient.generateImage(eq("Omelette"), anyList(), eq("A fluffy omelette"))).thenReturn(fakeBase64);
        when(s3UploadService.uploadImage(any(byte[].class), eq("Omelette"))).thenReturn(fakeUrl);
        ResponseEntity<SuccessResponseEntity<String>> response = controller.generateAndUploadImage(request);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(fakeUrl, response.getBody().getData());
    }
}
