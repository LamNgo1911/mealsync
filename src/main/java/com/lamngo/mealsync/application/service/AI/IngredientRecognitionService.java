package com.lamngo.mealsync.application.service.AI;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import com.lamngo.mealsync.application.data.KnownIngredients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class IngredientRecognitionService {
    private static final Logger logger = LoggerFactory.getLogger(IngredientRecognitionService.class);

    /**
     * Recognizes ingredients from an image using Google Vision API.
     * @param imageFile image file
     * @return Immutable list of recognized ingredient names
     * @throws IOException if the image is invalid or API call fails
     */
    public List<String> recognizeIngredients(MultipartFile imageFile) throws IOException {
        ByteString imgBytes = ByteString.readFrom(imageFile.getInputStream());
        return recognizeIngredients(imgBytes);
    }

    /**
     * Recognizes ingredients from an image using Google Vision API.
     * @param imgBytes image bytes
     * @return Immutable list of recognized ingredient names
     * @throws IOException if the image is invalid or API call fails
     */
    public List<String> recognizeIngredients(final ByteString imgBytes) throws IOException {
        if (imgBytes == null || imgBytes.isEmpty()) {
            logger.warn("Image is empty or null");
            throw new IOException("Image is empty or invalid");
        }

        try {
            List<String> ingredients = callGoogleVision(imgBytes);

            boolean containsFood = ingredients.stream()
                    .anyMatch(label -> label.toLowerCase().contains("food"));

            if (!containsFood) {
                logger.warn("No 'food' label found in image analysis");
                throw new IOException("No valid food-related labels detected");
            }

            ingredients.removeIf(ingredient -> !KnownIngredients.INGREDIENTS.contains(ingredient.toLowerCase()));

            return Collections.unmodifiableList(ingredients);
        } catch (Exception e) {
            logger.error("Failed to recognize ingredients: {}", e.getMessage(), e);
            throw new IOException("Failed to recognize ingredients", e);
        }
    }

    /**
     * Calls Google Vision API and extracts ingredient labels.
     */
    private List<String> callGoogleVision(final ByteString imgBytes) throws IOException {
        final Image img = Image.newBuilder().setContent(imgBytes).build();

        final Feature feat = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build();
        final AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            final List<AnnotateImageRequest> requests = Collections.singletonList(request);
            final BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            final AnnotateImageResponse res = response.getResponses(0);
            if (res.hasError()) {
                logger.error("Vision API error: {}", res.getError().getMessage());
                throw new IOException("Vision API error: " + res.getError().getMessage());
            }
            final List<String> ingredientNames = new ArrayList<>();
            for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                ingredientNames.add(annotation.getDescription());
            }
            return ingredientNames;
        }
    }
}
