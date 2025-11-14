package com.lamngo.mealsync.application.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@Slf4j
public class EmailService {
    
    private final SesClient sesClient;
    
    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;
    
    @Value("${AWS_SES_FROM_EMAIL}")
    private String fromEmail;

    public EmailService(
            @Value("${AWS_REGION}") String region,
            @Value("${AWS_ACCESS_KEY_ID}") String accessKeyId,
            @Value("${AWS_SECRET_ACCESS_KEY}") String secretAccessKey
    ) {
        this.sesClient = SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    public void sendVerificationEmail(String toEmail, String token, String userName) {
        try {
            String verificationUrl = baseUrl + "/api/v1/users/verify-email?token=" + token;
            log.info("Sending verification email to {} with URL: {}", toEmail, verificationUrl);
            String emailBody = String.format(
                "Hello %s,\n\n" +
                "Thank you for registering with Cookify!\n\n" +
                "Please click the link below to verify your email address:\n\n" +
                "%s\n\n" +
                "This link will expire in 24 hours.\n\n" +
                "If you didn't create this account, please ignore this email.\n\n" +
                "Best regards,\n" +
                "The Cookify Team",
                userName, verificationUrl
            );
            
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(toEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data("Verify your Cookify account")
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(emailBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();
            
            SendEmailResponse response = sesClient.sendEmail(emailRequest);
            log.info("Verification email sent to: {} with message ID: {}", toEmail, response.messageId());
        } catch (SesException e) {
            String errorMsg = String.format(
                "Failed to send verification email to %s. Error: %s. " +
                "Please check: 1) IAM user has 'ses:SendEmail' permission, " +
                "2) Sender email '%s' is verified in SES, " +
                "3) Recipient email is verified if SES is in sandbox mode.",
                toEmail, e.getMessage(), fromEmail
            );
            log.error(errorMsg, e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending verification email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token, String userName) {
        try {
            String resetUrl = baseUrl + "/api/v1/users/reset-password?token=" + token;
            String emailBody = String.format(
                "Hello %s,\n\n" +
                "We received a request to reset your password for your Cookify account.\n\n" +
                "Please click the link below to reset your password:\n\n" +
                "%s\n\n" +
                "This link will expire in 1 hour.\n\n" +
                "If you didn't request a password reset, please ignore this email. Your password will remain unchanged.\n\n" +
                "For security reasons, never share this link with anyone.\n\n" +
                "Best regards,\n" +
                "The Cookify Team",
                userName, resetUrl
            );
            
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(toEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data("Reset your Cookify password")
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(emailBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();
            
            SendEmailResponse response = sesClient.sendEmail(emailRequest);
            log.info("Password reset email sent to: {} with message ID: {}", toEmail, response.messageId());
        } catch (SesException e) {
            String errorMsg = String.format(
                "Failed to send password reset email to %s. Error: %s. " +
                "Please check: 1) IAM user has 'ses:SendEmail' permission, " +
                "2) Sender email '%s' is verified in SES, " +
                "3) Recipient email is verified if SES is in sandbox mode.",
                toEmail, e.getMessage(), fromEmail
            );
            log.error(errorMsg, e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending password reset email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}

