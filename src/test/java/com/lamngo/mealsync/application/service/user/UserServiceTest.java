package com.lamngo.mealsync.application.service.user;

import com.lamngo.mealsync.application.dto.user.UserPreferenceReadDto;
import com.lamngo.mealsync.application.dto.user.UserPreferenceUpdateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserUpdateDto;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.application.mapper.user.UserPreferenceMapper;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class UserServiceTest {
    @Mock private IUserRepo userRepo;
    @Mock private UserMapper userMapper;
    @Mock private UserPreferenceMapper userPreferenceMapper;
    @InjectMocks private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(userRepo, userPreferenceMapper, userMapper);
    }

    @Test
    void getAllUsers_success() {
        User user = new User();
        UserReadDto userReadDto = new UserReadDto();
        // Mock the expected behavior
        when(userRepo.findAll()).thenReturn(List.of(user));
        when(userMapper.toUserReadDto(any())).thenReturn(userReadDto);

        List<UserReadDto> users = userService.getAllUsers();
        // Verify the result
        assertEquals(List.of(userReadDto), users);
    }

    @Test
    void findUserById_success() {
        User user = new User();
        UserReadDto userReadDto = new UserReadDto();
        // Mock the expected behavior
        when(userRepo.findById(any())).thenReturn(Optional.of(user));
        when(userMapper.toUserReadDto(any())).thenReturn(userReadDto);

        UserReadDto result = userService.findUserById(user.getId());
        // Verify the result
        assertEquals(userReadDto, result);
    }

    @Test
    void findUserById_notFound() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.findUserById(id));
    }

    @Test
    void findUserByEmail_success() {
        User user = new User();
        UserReadDto userReadDto = new UserReadDto();
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(userMapper.toUserReadDto(any())).thenReturn(userReadDto);
        UserReadDto result = userService.findUserByEmail("test@example.com");
        assertEquals(userReadDto, result);
    }

    @Test
    void findUserByEmail_notFound() {
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.findUserByEmail("notfound@example.com"));
    }

    @Test
    void deleteById_success() {
        UUID id = UUID.randomUUID();
        User user = new User();
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        userService.deleteById(id);
    }

    @Test
    void deleteById_notFound() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteById(id));
    }

    @Test
    void updateUser_success() {
        UUID id = UUID.randomUUID();
        User user = new User();
        User updatedUser = new User();
        UserUpdateDto updateDto = new UserUpdateDto();
        UserReadDto userReadDto = new UserReadDto();
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(updatedUser);
        when(userMapper.toUserReadDto(updatedUser)).thenReturn(userReadDto);
        UserReadDto result = userService.updateUser(id, updateDto);
        assertEquals(userReadDto, result);
    }

    @Test
    void updateUser_notFound() {
        UUID id = UUID.randomUUID();
        UserUpdateDto updateDto = new UserUpdateDto();
        when(userRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(id, updateDto));
    }

    @Test
    void updateUserPreferencesById_success() {
        UUID id = UUID.randomUUID();
        User user = new User();
        UserPreference userPreference = new UserPreference();
        user.setUserPreference(userPreference);
        UserPreferenceUpdateDto updateDto = new UserPreferenceUpdateDto();
        UserPreferenceReadDto readDto = new UserPreferenceReadDto();
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        doNothing().when(userPreferenceMapper).updateUserPreferenceFromDto(updateDto, userPreference);
        when(userRepo.save(user)).thenReturn(user);
        when(userPreferenceMapper.toUserPreferenceReadDto(userPreference)).thenReturn(readDto);
        UserPreferenceReadDto result = userService.updateUserPreferencesById(id, updateDto);
        assertEquals(readDto, result);
    }

    @Test
    void updateUserPreferencesById_userNotFound() {
        UUID id = UUID.randomUUID();
        UserPreferenceUpdateDto updateDto = new UserPreferenceUpdateDto();
        when(userRepo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.updateUserPreferencesById(id, updateDto));
    }

    @Test
    void updateUserPreferencesById_preferenceNotFound() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setUserPreference(null);
        UserPreferenceUpdateDto updateDto = new UserPreferenceUpdateDto();
        when(userRepo.findById(id)).thenReturn(Optional.of(user));
        assertThrows(ResourceNotFoundException.class, () -> userService.updateUserPreferencesById(id, updateDto));
    }
}
