package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.PhotoRecipeRequest;
import com.lamngo.mealsync.application.service.AI.ImageGeneratorService;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final ImageGeneratorService imageGeneratorService;
    private final S3Service s3UploadService;

    public PhotoController(ImageGeneratorService imageGeneratorService, S3Service s3UploadService) {
        this.imageGeneratorService = imageGeneratorService;
        this.s3UploadService = s3UploadService;
    }

    @PostMapping("/generate")
    public ResponseEntity<String> generateAndUploadImage(@RequestBody PhotoRecipeRequest request) {

        String apiResponse = imageGeneratorService.generateImage(request.getRecipeName(), request.getIngredients(), request.getDescription());
        byte[] imageBytes = decodeBase64ToBytes(apiResponse);

        String imageUrl = s3UploadService.uploadImage(imageBytes, request.getRecipeName());

        return ResponseEntity.ok(imageUrl);
    }

    private byte[] decodeBase64ToBytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

}
