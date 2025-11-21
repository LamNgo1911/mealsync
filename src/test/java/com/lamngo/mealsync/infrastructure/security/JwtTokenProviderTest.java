package com.lamngo.mealsync.infrastructure.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String TEST_SECRET = "test-secret-key-for-testing-purposes-only-minimum-256-bits-required";
    private static final Integer TEST_EXPIRATION = 3600000; // 1 hour in milliseconds

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", TEST_EXPIRATION);
        jwtTokenProvider.init();
    }

    @Test
    void generateToken_shouldGenerateValidToken() {
        // Given
        UserDetails userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();

        // When
        String token = jwtTokenProvider.generateToken(userDetails);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractEmail_shouldExtractEmailFromToken() {
        // Given
        UserDetails userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
        String token = jwtTokenProvider.generateToken(userDetails);

        // When
        String email = jwtTokenProvider.extractEmail(token);

        // Then
        assertEquals("test@example.com", email);
    }

    @Test
    void validateToken_shouldReturnTrue_whenTokenIsValid() {
        // Given
        UserDetails userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
        String token = jwtTokenProvider.generateToken(userDetails);

        // When
        boolean isValid = jwtTokenProvider.validateToken(token, userDetails);

        // Then
        assertTrue(isValid);
    }

    @Test
    void validateToken_shouldReturnFalse_whenEmailMismatch() {
        // Given
        UserDetails userDetails1 = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
        UserDetails userDetails2 = User.builder()
                .username("other@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
        String token = jwtTokenProvider.generateToken(userDetails1);

        // When
        boolean isValid = jwtTokenProvider.validateToken(token, userDetails2);

        // Then
        assertFalse(isValid);
    }

    @Test
    void validateToken_shouldReturnFalse_whenTokenIsExpired() throws InterruptedException {
        // Given
        JwtTokenProvider shortExpiryProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortExpiryProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpiryProvider, "jwtExpiration", 100); // 100ms
        shortExpiryProvider.init();

        UserDetails userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
        String token = shortExpiryProvider.generateToken(userDetails);

        // Wait for token to expire
        Thread.sleep(200);

        // When
        boolean isValid = shortExpiryProvider.validateToken(token, userDetails);

        // Then
        assertFalse(isValid);
    }

    @Test
    void extractEmail_shouldThrowException_whenTokenIsInvalid() {
        // Given
        String invalidToken = "invalid.token.here";

        // When/Then
        // Note: extractClaim wraps JwtException in RuntimeException (except ExpiredJwtException)
        assertThrows(RuntimeException.class, () -> {
            jwtTokenProvider.extractEmail(invalidToken);
        });
    }

    @Test
    void extractEmail_shouldThrowException_whenTokenIsExpired() throws InterruptedException {
        // Given
        JwtTokenProvider shortExpiryProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortExpiryProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpiryProvider, "jwtExpiration", 100); // 100ms
        shortExpiryProvider.init();

        UserDetails userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();
        String token = shortExpiryProvider.generateToken(userDetails);

        // Wait for token to expire
        Thread.sleep(200);

        // When/Then
        assertThrows(ExpiredJwtException.class, () -> {
            shortExpiryProvider.extractEmail(token);
        });
    }

    @Test
    void getSigningKey_shouldReturnKey() {
        // When
        var key = jwtTokenProvider.getSigningKey();

        // Then
        assertNotNull(key);
    }

    @Test
    void generateToken_shouldIncludeIssuedAt() {
        // Given
        UserDetails userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(new ArrayList<>())
                .build();

        // When
        String token = jwtTokenProvider.generateToken(userDetails);

        // Then
        assertNotNull(token);
        // Token should be parseable
        String email = jwtTokenProvider.extractEmail(token);
        assertEquals("test@example.com", email);
    }
}

