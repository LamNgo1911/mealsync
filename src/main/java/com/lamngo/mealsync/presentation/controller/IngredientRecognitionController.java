package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.service.AI.IngredientRecognitionService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<String>> recognizeIngredients(@RequestParam("image") MultipartFile image) {
        List<String> ingredients = ingredientRecognitionService.recognizeIngredients(image);
        return ResponseEntity.ok(ingredients);
    }
}
