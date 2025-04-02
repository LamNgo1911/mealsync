package com.lamngo.mealsync.application.service.user;

import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserUpdateDto;

import java.util.List;
import java.util.UUID;

public interface IUserService {
    List<UserReadDto> getAllUsers();
    UserReadDto findUserById(UUID id);
    UserReadDto findUserByEmail(String email);
    void deleteById(UUID id);
    UserReadDto updateUser(UUID id, UserUpdateDto userUpdateDto);
}
