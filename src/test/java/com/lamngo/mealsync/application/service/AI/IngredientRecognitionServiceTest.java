package com.lamngo.mealsync.application.service.AI;

import com.google.protobuf.ByteString;
import com.lamngo.mealsync.application.data.KnownIngredients;
import com.lamngo.mealsync.presentation.error.IngredientRecognitionServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngredientRecognitionServiceTest {
    private IngredientRecognitionService service;

    @BeforeEach
    void setUp() {
        service = spy(new IngredientRecognitionService());
    }

    @Test
    void recognizeIngredients_withValidImageFile_returnsIngredients() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        byte[] fakeBytes = "image-bytes".getBytes();
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(fakeBytes));
        ByteString byteString = ByteString.copyFrom(fakeBytes);
        String realIngredient = KnownIngredients.INGREDIENTS.iterator().next();
        List<String> visionResult = List.of("food", realIngredient);
        doReturn(visionResult).when(service).callGoogleVision(byteString);
        List<String> result = service.recognizeIngredients(file);
        assertEquals(List.of(realIngredient), result);
    }

    @Test
    void recognizeIngredients_withNullBytes_throwsException() {
        Exception ex = assertThrows(IngredientRecognitionServiceException.class, () ->
                service.recognizeIngredients((ByteString) null));
        assertTrue(ex.getMessage().toLowerCase().contains("empty"));
    }

    @Test
    void recognizeIngredients_withNoFoodLabel_throwsException() throws Exception {
        ByteString byteString = ByteString.copyFromUtf8("image-bytes");
        // Use only real ingredient(s) that are not "food"
        String realIngredient = KnownIngredients.INGREDIENTS.iterator().next();
        doReturn(List.of(realIngredient)).when(service).callGoogleVision(byteString);
        Exception ex = assertThrows(IngredientRecognitionServiceException.class, () ->
                service.recognizeIngredients(byteString));
        assertTrue(ex.getMessage().toLowerCase().contains("food"));
    }

    @Test
    void recognizeIngredients_filtersUnknownIngredients() throws Exception {
        ByteString byteString = ByteString.copyFromUtf8("image-bytes");
        String realIngredient = KnownIngredients.INGREDIENTS.iterator().next();
        doReturn(List.of("food", realIngredient, "unknown"))
                .when(service).callGoogleVision(byteString);
        List<String> result = service.recognizeIngredients(byteString);
        assertEquals(List.of(realIngredient), result);
    }
}
