package com.lamngo.mealsync.infrastructure.security;

import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private IUserRepo userRepo;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(java.util.UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setName("Test User");
        testUser.setRole(UserRole.USER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setEmailVerified(true);
    }

    @Test
    void loadUserByUsername_shouldReturnUserDetails_whenUserExists() {
        // Given
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // When
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("test@example.com");

        // Then
        assertNotNull(userDetails);
        assertEquals("test@example.com", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        verify(userRepo, times(1)).findByEmail("test@example.com");
    }

    @Test
    void loadUserByUsername_shouldThrowException_whenUserNotFound() {
        // Given
        when(userRepo.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // When/Then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername("nonexistent@example.com");
        });

        assertEquals("User not found with email: nonexistent@example.com", exception.getMessage());
        verify(userRepo, times(1)).findByEmail("nonexistent@example.com");
    }

    @Test
    void loadUserByUsername_shouldReturnUserWithAuthorities() {
        // Given
        testUser.setRole(UserRole.ADMIN);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // When
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("test@example.com");

        // Then
        assertNotNull(userDetails);
        assertNotNull(userDetails.getAuthorities());
        verify(userRepo, times(1)).findByEmail("test@example.com");
    }

    @Test
    void loadUserByUsername_shouldHandleDifferentEmailCases() {
        // Given
        when(userRepo.findByEmail("TEST@EXAMPLE.COM")).thenReturn(Optional.of(testUser));

        // When
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("TEST@EXAMPLE.COM");

        // Then
        assertNotNull(userDetails);
        verify(userRepo, times(1)).findByEmail("TEST@EXAMPLE.COM");
    }
}

