package com.lamngo.mealsync.application.service.AWS;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


@Service
@Transactional
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public S3Service(
            @Value("${aws.region}") String region,
            @Value("${aws.access-key-id}") String accessKeyId,
            @Value("${aws.secret-access-key}") String secretAccessKey
    ) {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    public String uploadImage(byte[] imageBytes, String imageName) {
        try {
            Path filePath = Files.createTempFile(imageName, ".png");
            Files.write(filePath, imageBytes);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("recipes/" + imageName + ".png")
                    .contentType("image/png")
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));

            Files.deleteIfExists(filePath);
            System.out.println("Image uploaded to S3: " + imageName);
            return "https://" + bucketName + ".s3.amazonaws.com/recipes/" + imageName + ".png";
        } catch (IOException | S3Exception e) {
            // Log the error (replace with your logging framework)
            System.err.println("Error uploading image to S3: " + e.getMessage());
            throw new RuntimeException("Failed to upload image", e);
        }
    }

}
