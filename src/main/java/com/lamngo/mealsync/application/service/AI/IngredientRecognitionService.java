package com.lamngo.mealsync.application.service.AI;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.lamngo.mealsync.application.data.KnownIngredients;
import com.lamngo.mealsync.presentation.error.IngredientRecognitionServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class IngredientRecognitionService {
    private static final Logger logger = LoggerFactory.getLogger(IngredientRecognitionService.class);

    @Value("${GOOGLE_APPLICATION_CREDENTIALS}")
    private String credentialsPath;

    public List<String> recognizeIngredients(MultipartFile imageFile) throws IngredientRecognitionServiceException {
        try {
            ByteString imgBytes = ByteString.readFrom(imageFile.getInputStream());
            return recognizeIngredients(imgBytes);
        } catch (IOException e) {
            throw new IngredientRecognitionServiceException("Failed to read image file", e);
        }
    }

    public List<String> recognizeIngredients(final ByteString imgBytes) throws IngredientRecognitionServiceException {
        if (imgBytes == null || imgBytes.isEmpty()) {
            logger.warn("Image is empty or null");
            throw new IngredientRecognitionServiceException("Image is empty or invalid");
        }

        try {
            List<String> ingredients = new ArrayList<>(callGoogleVision(imgBytes));

            boolean containsFood = ingredients.stream()
                    .anyMatch(label -> label.toLowerCase().contains("food"));

            if (!containsFood) {
                logger.warn("No 'food' label found in image analysis");
                throw new IngredientRecognitionServiceException("No valid food-related labels detected");
            }

            ingredients.removeIf(ingredient -> !KnownIngredients.INGREDIENTS.contains(ingredient.toLowerCase()));

            return Collections.unmodifiableList(ingredients);
        } catch (IngredientRecognitionServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to recognize ingredients: {}", e.getMessage(), e);
            throw new IngredientRecognitionServiceException("Failed to recognize ingredients", e);
        }
    }

    public List<String> callGoogleVision(final ByteString imgBytes) throws IngredientRecognitionServiceException {
        try {
            final Image img = Image.newBuilder().setContent(imgBytes).build();

            final Feature feat = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build();
            final AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            File credFile = new File(credentialsPath);
            if (!credFile.exists()) {
                logger.error("Credentials file does not exist at path: {}", credentialsPath);
            }

            // ✅ Load credentials dynamically
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));

            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
                final List<AnnotateImageRequest> requests = Collections.singletonList(request);
                final BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                final List<AnnotateImageResponse> responses = response.getResponsesList();
                List<String> labels = new ArrayList<>();
                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) {
                        logger.error("Google Vision API error: {}", res.getError().getMessage());
                        throw new IngredientRecognitionServiceException("Google Vision API error: " + res.getError().getMessage());
                    }
                    for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                        labels.add(annotation.getDescription());
                    }
                }
                return labels;
            }
        } catch (IngredientRecognitionServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error calling Google Vision API: {}", e.getMessage(), e);
            throw new IngredientRecognitionServiceException("Error calling Google Vision API", e);
        }
    }
}
