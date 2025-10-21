package com.lamngo.mealsync.application.service.AWS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ServiceTest {
    @Mock
    private S3Client s3Client;
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Provide dummy AWS values for region, accessKeyId, secretAccessKey
        s3Service = new S3Service("us-east-1", "dummy-key", "dummy-secret");
        // Set bucketName and region via reflection since they're injected by @Value
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");
        // Inject mock s3Client
        ReflectionTestUtils.setField(s3Service, "s3Client", s3Client);
    }

    @Test
    void uploadImage_success() {
        byte[] imageBytes = new byte[]{1, 2, 3};
        String originalImageName = "test.png";

        // Mock S3Client putObject
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Call method
        String resultUrl = s3Service.uploadImage(imageBytes, originalImageName);

        // Verify the URL format is correct (permanent public URL)
        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith("https://test-bucket.s3.us-east-1.amazonaws.com/recipes/"));
        assertTrue(resultUrl.endsWith(".png"));

        // Verify putObject was called
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadImage_s3Exception() {
        byte[] imageBytes = new byte[]{1, 2, 3};
        String originalImageName = "test.png";
        doThrow(S3Exception.builder().message("S3 error").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        Exception exception = assertThrows(RuntimeException.class, () ->
                s3Service.uploadImage(imageBytes, originalImageName));
        assertTrue(exception.getMessage().contains("Failed to upload image"));
    }

    @Test
    void deleteImage_success() {
        String imageUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/recipes/test-image.png";

        // Mock S3Client deleteObject
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
            .thenReturn(DeleteObjectResponse.builder().build());

        // Call method
        s3Service.deleteImage(imageUrl);

        // Verify deleteObject was called
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteImage_s3Exception() {
        String imageUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/recipes/test-image.png";

        doThrow(S3Exception.builder().message("S3 error").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        Exception exception = assertThrows(RuntimeException.class, () ->
                s3Service.deleteImage(imageUrl));
        assertTrue(exception.getMessage().contains("Failed to delete image"));
    }

    @Test
    void deleteImage_invalidUrl() {
        String invalidUrl = "invalid-url";

        // Should not throw exception, just log warning and return
        assertDoesNotThrow(() -> s3Service.deleteImage(invalidUrl));

        // Verify deleteObject was never called
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
