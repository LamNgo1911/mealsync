package com.lamngo.mealsync.application.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SesClient sesClient;

    private EmailService emailService;
    private static final String TEST_BASE_URL = "https://api.cookify.dev";
    private static final String TEST_FROM_EMAIL = "noreply@cookify.dev";

    @BeforeEach
    void setUp() {
        // Note: EmailService constructor creates a real SesClient, so we need to use reflection
        // to replace it with a mock after construction
        emailService = new EmailService("us-east-1", "test-key", "test-secret");
        ReflectionTestUtils.setField(emailService, "sesClient", sesClient);
        ReflectionTestUtils.setField(emailService, "baseUrl", TEST_BASE_URL);
        ReflectionTestUtils.setField(emailService, "fromEmail", TEST_FROM_EMAIL);
    }

    @Test
    void sendVerificationEmail_shouldSendEmailSuccessfully() {
        // Given
        String toEmail = "user@example.com";
        String token = "test-token-123";
        String userName = "Test User";

        SendEmailResponse response = SendEmailResponse.builder()
                .messageId("test-message-id")
                .build();

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

        // When
        assertDoesNotThrow(() -> {
            emailService.sendVerificationEmail(toEmail, token, userName);
        });

        // Then
        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, times(1)).sendEmail(requestCaptor.capture());

        SendEmailRequest request = requestCaptor.getValue();
        assertEquals(TEST_FROM_EMAIL, request.source());
        assertEquals(toEmail, request.destination().toAddresses().get(0));
        assertEquals("Verify your Cookify account", request.message().subject().data());
        assertTrue(request.message().body().text().data().contains("Hello Test User"));
        assertTrue(request.message().body().text().data().contains(TEST_BASE_URL + "/api/v1/users/verify-email?token=" + token));
    }

    @Test
    void sendPasswordResetEmail_shouldSendEmailSuccessfully() {
        // Given
        String toEmail = "user@example.com";
        String token = "reset-token-456";
        String userName = "Test User";

        SendEmailResponse response = SendEmailResponse.builder()
                .messageId("test-message-id")
                .build();

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

        // When
        assertDoesNotThrow(() -> {
            emailService.sendPasswordResetEmail(toEmail, token, userName);
        });

        // Then
        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, times(1)).sendEmail(requestCaptor.capture());

        SendEmailRequest request = requestCaptor.getValue();
        assertEquals(TEST_FROM_EMAIL, request.source());
        assertEquals(toEmail, request.destination().toAddresses().get(0));
        assertEquals("Reset your Cookify password", request.message().subject().data());
        assertTrue(request.message().body().text().data().contains("Hello Test User"));
        assertTrue(request.message().body().text().data().contains(TEST_BASE_URL + "/api/v1/users/reset-password?token=" + token));
    }

    @Test
    void sendVerificationEmail_shouldThrowException_whenSesFails() {
        // Given
        String toEmail = "user@example.com";
        String token = "test-token";
        String userName = "Test User";

        SesException sesException = mock(SesException.class);
        when(sesException.getMessage()).thenReturn("SES error");

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            emailService.sendVerificationEmail(toEmail, token, userName);
        });

        assertTrue(exception.getMessage().contains("Failed to send verification email"));
        verify(sesClient, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void sendPasswordResetEmail_shouldThrowException_whenSesFails() {
        // Given
        String toEmail = "user@example.com";
        String token = "reset-token";
        String userName = "Test User";

        SesException sesException = mock(SesException.class);
        when(sesException.getMessage()).thenReturn("SES error");

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            emailService.sendPasswordResetEmail(toEmail, token, userName);
        });

        assertTrue(exception.getMessage().contains("Failed to send password reset email"));
        verify(sesClient, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void sendVerificationEmail_shouldHandleGenericException() {
        // Given
        String toEmail = "user@example.com";
        String token = "test-token";
        String userName = "Test User";

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(new RuntimeException("Unexpected error"));

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            emailService.sendVerificationEmail(toEmail, token, userName);
        });

        assertTrue(exception.getMessage().contains("Failed to send verification email"));
    }
}

