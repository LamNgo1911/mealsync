package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;

public interface IAuthService {
    UserReadDto register(UserCreateDto userCreateDto);
    String login(UserLoginDto userLoginDto);
}
