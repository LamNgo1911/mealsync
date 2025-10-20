package com.lamngo.mealsync.application.service.AWS;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for interacting with AWS S3 (permanent public URLs).
 */
@Service
@Transactional
public class S3Service {

    private final S3Client s3Client;
    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    @Value("${AWS_S3_BUCKET_NAME}")
    private String bucketName;

    @Value("${AWS_REGION}")
    private String region;

    public S3Service(
            @Value("${AWS_REGION}") String region,
            @Value("${AWS_ACCESS_KEY_ID}") String accessKeyId,
            @Value("${AWS_SECRET_ACCESS_KEY}") String secretAccessKey
    ) {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    public String uploadImage(byte[] imageBytes, String originalImageName) {
        try {
            String key = "recipes/" + UUID.randomUUID().toString() + ".png";

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .acl("public-read") // 👈 Make the object publicly accessible
                    .contentType("image/png")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));

            // ✅ Return permanent public URL
            String imageUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;

            log.info("Image uploaded successfully: {}", imageUrl);
            return imageUrl;

        } catch (S3Exception e) {
            log.error("Error uploading image to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload image", e);
        } catch (Exception e) {
            log.error("General error uploading image to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload image", e);
        }
    }

    public void deleteImage(String imageUrl) {
        try {
            // Extract the S3 key from the full URL
            // URL format: https://{bucket}.s3.{region}.amazonaws.com/{key}
            String key = extractKeyFromUrl(imageUrl);

            if (key == null || key.isEmpty()) {
                log.warn("Invalid image URL, cannot extract key: {}", imageUrl);
                return;
            }

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Image deleted successfully from S3: {}", key);

        } catch (S3Exception e) {
            log.error("Error deleting image from S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete image", e);
        } catch (Exception e) {
            log.error("General error deleting image from S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete image", e);
        }
    }

    private String extractKeyFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            // URL format: https://{bucket}.s3.{region}.amazonaws.com/{key}
            // Extract everything after .amazonaws.com/
            String pattern = ".amazonaws.com/";
            int index = imageUrl.indexOf(pattern);
            if (index != -1) {
                return imageUrl.substring(index + pattern.length());
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting key from URL: {}", imageUrl, e);
            return null;
        }
    }
}
