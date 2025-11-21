package com.lamngo.mealsync.presentation.error;

import com.lamngo.mealsync.presentation.shared.ErrorResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        // No setup needed - stubbing will be done per test
    }

    @Test
    void handleAuthorizationDeniedException_shouldReturnForbidden() {
        // Given
        when(response.isCommitted()).thenReturn(false);
        AuthorizationDeniedException ex = mock(AuthorizationDeniedException.class);
        when(ex.getMessage()).thenReturn("Access denied");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleAuthorizationDeniedException(
                ex, request, this.response);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertTrue(response.getBody().getErrors().get(0).getMessage().contains("Access Denied"));
    }

    @Test
    void handleAuthorizationDeniedException_shouldReturnNull_whenResponseCommitted() {
        // Given
        when(response.isCommitted()).thenReturn(true);
        AuthorizationDeniedException ex = mock(AuthorizationDeniedException.class);
        when(ex.getMessage()).thenReturn("Access denied");

        // When
        ResponseEntity<ErrorResponseEntity> result = exceptionHandler.handleAuthorizationDeniedException(
                ex, request, response);

        // Then
        assertNull(result);
    }

    @Test
    void handleAllExceptions_shouldReturnInternalServerError() {
        // Given
        Exception ex = new Exception("Unexpected error");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleAllExceptions(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertTrue(response.getBody().getErrors().get(0).getMessage().contains("unexpected error"));
    }

    @Test
    void handleMethodArgumentNotValidException_shouldReturnBadRequest() {
        // Given
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "default message");
        List<FieldError> fieldErrors = List.of(fieldError);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleMethodArgumentNotValidException(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertEquals("field", response.getBody().getErrors().get(0).getField());
        assertEquals("default message", response.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void handleBadRequestException_shouldReturnBadRequest() {
        // Given
        BadRequestException ex = new BadRequestException("Invalid request");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleBadRequestException(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertEquals("Invalid request", response.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void handleResourceNotFoundException_shouldReturnNotFound() {
        // Given
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleResourceNotFoundException(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertEquals("Resource not found", response.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void handleUnauthorizedException_shouldReturnUnauthorized() {
        // Given
        UnauthorizedException ex = new UnauthorizedException("Unauthorized");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleUnauthorizedException(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertEquals("Unauthorized", response.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void handleForbiddenException_shouldReturnForbidden() {
        // Given
        ForbiddenException ex = new ForbiddenException("Forbidden");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleForbiddenException(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertEquals("Forbidden", response.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void handleServiceExceptions_shouldReturnBadGateway() {
        // Given
        GeminiServiceException ex = new GeminiServiceException("Service error");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleServiceExceptions(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals(1, response.getBody().getErrors().size());
        assertEquals("Service error", response.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void handleServiceExceptions_shouldHandleImageGeneratorServiceException() {
        // Given
        ImageGeneratorServiceException ex = new ImageGeneratorServiceException("Image generation failed");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleServiceExceptions(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    void handleServiceExceptions_shouldHandleIngredientRecognitionServiceException() {
        // Given
        IngredientRecognitionServiceException ex = new IngredientRecognitionServiceException("Recognition failed");

        // When
        ResponseEntity<ErrorResponseEntity> response = exceptionHandler.handleServiceExceptions(ex);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }
}

