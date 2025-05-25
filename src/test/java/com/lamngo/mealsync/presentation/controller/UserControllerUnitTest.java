package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.service.user.UserService;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerUnitTest {
    @Mock UserService userService;
    @Mock UserDetails userDetails;
    @InjectMocks UserController controller;

    @BeforeEach
    void setup() { MockitoAnnotations.openMocks(this); }

    @Test
    void getAllUsers_success() {
        List<UserReadDto> users = List.of(new UserReadDto());
        when(userService.getAllUsers()).thenReturn(users);
        ResponseEntity<SuccessResponseEntity<List<UserReadDto>>> response = controller.getAllUsers();
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(users, response.getBody().getData());
    }

    @Test
    void findUserById_success() {
        UUID id = UUID.randomUUID();
        UserReadDto user = new UserReadDto();
        when(userService.findUserById(id)).thenReturn(user);
        ResponseEntity<SuccessResponseEntity<UserReadDto>> response = controller.findUserById(id, userDetails);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(user, response.getBody().getData());
    }

    @Test
    void deleteUserById_success() {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).deleteById(id);

        // Mock SecurityContext and Authentication
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getAuthorities()).thenReturn(java.util.Collections.emptyList());
        SecurityContextHolder.setContext(securityContext);

        ResponseEntity<Void> response = controller.deleteUserById(id);
        assertEquals(204, response.getStatusCodeValue());
        verify(userService).deleteById(id);

        // Clear context after test
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUser_success() {
        UUID id = UUID.randomUUID();
        UserUpdateDto updateDto = new UserUpdateDto();
        UserReadDto updatedUser = new UserReadDto();
        when(userService.updateUser(id, updateDto)).thenReturn(updatedUser);
        ResponseEntity<SuccessResponseEntity<UserReadDto>> response = controller.updateUser(id, updateDto);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(updatedUser, response.getBody().getData());
    }

    @Test
    void updateUserPreferenceById_success() {
        UUID id = UUID.randomUUID();
        UserPreferenceUpdateDto prefDto = new UserPreferenceUpdateDto();
        UserPreferenceReadDto prefReadDto = new UserPreferenceReadDto();
        UserReadDto userReadDto = new UserReadDto();
        when(userService.updateUserPreferencesById(id, prefDto)).thenReturn(prefReadDto);
        when(userService.findUserById(id)).thenReturn(userReadDto);
        // The controller sets the preference on the userReadDto
        ResponseEntity<SuccessResponseEntity<UserReadDto>> response = controller.updateUserPreferenceById(id, prefDto);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(userReadDto, response.getBody().getData());
        assertEquals(prefReadDto, response.getBody().getData().getUserPreference());
    }
}
