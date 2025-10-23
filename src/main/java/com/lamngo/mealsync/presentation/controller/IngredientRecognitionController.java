package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.service.AI.IngredientRecognitionService;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingredient-recognition")
public class IngredientRecognitionController {
    private final IngredientRecognitionService ingredientRecognitionService;

    public IngredientRecognitionController(IngredientRecognitionService ingredientRecognitionService) {
        this.ingredientRecognitionService = ingredientRecognitionService;
    }

    @PostMapping("/detect")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponseEntity<List<String>>> recognizeIngredients(@RequestParam("image") MultipartFile image) {
        List<String> ingredients = ingredientRecognitionService.recognizeIngredients(image);
        SuccessResponseEntity<List<String>> body = new SuccessResponseEntity<>();
        body.setData(ingredients);

        return ResponseEntity.ok(body);
    }
}
