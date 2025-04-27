package com.lamngo.mealsync.application.service.AI;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import com.lamngo.mealsync.application.data.KnownIngredients;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngredientRecognitionService {
    public List<String> recognizeIngredients(MultipartFile imageFile) throws IOException {

        List<String> ingredients = new ArrayList<>();
        try {
            ByteString imgBytes = ByteString.readFrom(imageFile.getInputStream());

            if (imgBytes.isEmpty()) {
                throw new IOException("Image is empty or invalid");
            }
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {

                List<AnnotateImageRequest> requests = new ArrayList<>();
                requests.add(request);
                List<AnnotateImageResponse> responses = client.batchAnnotateImages(requests).getResponsesList();

                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) {
                        System.out.println("Error from Vision API: " + res.getError().getMessage());
                        throw new IOException("Error from Vision API: " + res.getError().getMessage());
                    }

                    for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                        String label = annotation.getDescription().toLowerCase();
                        System.out.println("Label: " + label + ", Score: " + annotation.getScore());
                        if (KnownIngredients.INGREDIENTS.contains(label)) {
                            System.out.println("Matched ingredient: " + label);
                            ingredients.add(label);
                        }
                    }
                }
            }
            System.out.println("Recognized ingredients: " + ingredients);
            return ingredients.stream().distinct().toList();
        } catch (Exception e) {
            System.out.println("Exception occurred: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
