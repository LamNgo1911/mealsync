package com.lamngo.mealsync.application.service;

import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserCreateDto;

import java.util.List;

public interface IUserService {
    UserReadDto register(UserCreateDto userCreateDto);
    UserReadDto login(String email, String password);
    List<UserReadDto> getAllUsers();
    UserReadDto findUserById(String id);
    UserReadDto findUserByEmail(String email);
    void deleteById(String id);
}
