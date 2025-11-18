package com.lamngo.mealsync.presentation.error;

import com.lamngo.mealsync.presentation.shared.ErrorEntity;
import com.lamngo.mealsync.presentation.shared.ErrorResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponseEntity> handleAuthorizationDeniedException(
            AuthorizationDeniedException ex, HttpServletRequest request, HttpServletResponse response) {
        // Check if response is already committed (common in async/SSE scenarios)
        if (response.isCommitted()) {
            logger.warn("AuthorizationDeniedException occurred but response is already committed. " +
                    "This is likely due to async operation or SSE stream. Request: {}", request.getRequestURI());
            // Return null to indicate we can't send a response
            return null;
        }

        ErrorEntity errorEntity = ErrorEntity.builder()
                .field("Authorization")
                .message("Access Denied: " + ex.getMessage())
                .build();

        ErrorResponseEntity errorResponseEntity = ErrorResponseEntity.builder()
                .errors(List.of(errorEntity))
                .build();

        return new ResponseEntity<>(errorResponseEntity, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponseEntity> handleAllExceptions(Exception ex) {
        ErrorResponseEntity errorResponseEntity = new ErrorResponseEntity();
        ErrorEntity errorEntity = new ErrorEntity();
        errorEntity.setMessage("An unexpected error occurred: " + ex.getMessage());
        errorEntity.setField("Unknown");
        List<ErrorEntity> errors = new ArrayList<>();
        errors.add(errorEntity);
        errorResponseEntity.setErrors(errors);

        return new ResponseEntity<>(errorResponseEntity, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponseEntity> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ErrorResponseEntity errorResponseEntity = new ErrorResponseEntity();
        List<ErrorEntity> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            ErrorEntity errorEntity = new ErrorEntity();
            errorEntity.setMessage(error.getDefaultMessage());
            errorEntity.setField(error.getField());
            errors.add(errorEntity);
        });
        errorResponseEntity.setErrors(errors);

        return new ResponseEntity<>(errorResponseEntity, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponseEntity> handleBadRequestException(Exception ex) {
        ErrorEntity errorEntity = ErrorEntity.builder()
                .field("Bad Request")
                .message(ex.getMessage())
                .build();

        ErrorResponseEntity errorResponseEntity = ErrorResponseEntity.builder()
                .errors(List.of(errorEntity))
                .build();

        return new ResponseEntity<>(errorResponseEntity, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponseEntity> handleResourceNotFoundException(Exception ex) {
        ErrorEntity errorEntity = ErrorEntity.builder()
                .field("Resource")
                .message(ex.getMessage())
                .build();

        ErrorResponseEntity errorResponseEntity = ErrorResponseEntity.builder()
                .errors(List.of(errorEntity))
                .build();

        return new ResponseEntity<>(errorResponseEntity, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ErrorResponseEntity> handleUnauthorizedException(Exception ex) {
        ErrorEntity errorEntity = ErrorEntity.builder()
                .field("Unauthorized")
                .message(ex.getMessage())
                .build();
        ErrorResponseEntity errorResponseEntity = ErrorResponseEntity.builder()
                .errors(List.of(errorEntity))
                .build();
        return new ResponseEntity<>(errorResponseEntity, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponseEntity> handleForbiddenException(Exception ex) {
        ErrorEntity errorEntity = ErrorEntity.builder()
                .field("Forbidden")
                .message(ex.getMessage())
                .build();
        ErrorResponseEntity errorResponseEntity = ErrorResponseEntity.builder()
                .errors(List.of(errorEntity))
                .build();
        return new ResponseEntity<>(errorResponseEntity, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({
            GeminiServiceException.class,
            ImageGeneratorServiceException.class,
            IngredientRecognitionServiceException.class
    })
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ResponseEntity<ErrorResponseEntity> handleServiceExceptions(RuntimeException ex) {
        String field = extractServiceNameFromException(ex);

        ErrorEntity errorEntity = ErrorEntity.builder()
                .field(field)
                .message(ex.getMessage())
                .build();

        ErrorResponseEntity errorResponseEntity = ErrorResponseEntity.builder()
                .errors(List.of(errorEntity))
                .build();

        return new ResponseEntity<>(errorResponseEntity, HttpStatus.BAD_GATEWAY);
    }

    private String extractServiceNameFromException(RuntimeException ex) {
        // Return simple class name as the service identifier
        return ex.getClass().getSimpleName().replace("Exception", "");
    }}

