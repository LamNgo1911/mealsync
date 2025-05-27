package com.lamngo.mealsync.application.service.user;

import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.application.mapper.user.UserPreferenceMapper;
import com.lamngo.mealsync.domain.model.user.User;
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
        UUID userId = UUID.randomUUID();
        // Mock the expected behavior
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

       assertThrows(ResourceNotFoundException.class, () -> {
            userService.findUserById(userId);
        });
    }

    @Test
    void findUserByEmail_success() {
        User user = new User();
        UserReadDto userReadDto = new UserReadDto();
        // Mock the expected behavior
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(userMapper.toUserReadDto(any())).thenReturn(userReadDto);

        UserReadDto result = userService.findUserByEmail(user.getEmail());
        // Verify the result
        assertEquals(userReadDto, result);
    }

    @Test
    void findUserByEmail_notFound() {
        String email = "zV0Hq@example.com";
        // Mock the expected behavior
        when(userRepo.findByEmail(email)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> {
            userService.findUserByEmail(email);
        });
    }

}
