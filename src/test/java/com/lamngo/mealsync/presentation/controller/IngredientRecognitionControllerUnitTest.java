package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.service.AI.IngredientRecognitionService;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngredientRecognitionControllerUnitTest {
    @Mock IngredientRecognitionService ingredientRecognitionService;
    @Mock MultipartFile image;
    @InjectMocks IngredientRecognitionController controller;

    @BeforeEach
    void setup() { MockitoAnnotations.openMocks(this); }

    @Test
    void recognizeIngredients_success() {
        List<String> mockIngredients = List.of("egg", "milk");
        when(ingredientRecognitionService.recognizeIngredients(image)).thenReturn(mockIngredients);
        ResponseEntity<SuccessResponseEntity<List<String>>> response = controller.recognizeIngredients(image);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(mockIngredients, response.getBody().getData());
    }
}
