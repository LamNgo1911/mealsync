package com.lamngo.mealsync.application.service.AWS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ServiceTest {
    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @InjectMocks
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set bucketName via reflection since it's injected by @Value
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
    }

    @Test
    void uploadImage_success() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3};
        String originalImageName = "test.png";

        // Mock S3Client putObject
        doNothing().when(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));

        // Mock presigned URL
        URL fakeUrl = new URL("https://example.com/fake-presigned-url");
        var presignResult = mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
        when(presignResult.url()).thenReturn(fakeUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignResult);

        // Call method
        String resultUrl = s3Service.uploadImage(imageBytes, originalImageName);
        assertEquals("https://example.com/fake-presigned-url", resultUrl);
    }

    @Test
    void uploadImage_s3Exception() {
        byte[] imageBytes = new byte[]{1, 2, 3};
        String originalImageName = "test.png";
        doThrow(S3Exception.builder().message("S3 error").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        Exception exception = assertThrows(RuntimeException.class, () ->
                s3Service.uploadImage(imageBytes, originalImageName));
        assertTrue(exception.getMessage().contains("Failed to upload image"));
    }
}
