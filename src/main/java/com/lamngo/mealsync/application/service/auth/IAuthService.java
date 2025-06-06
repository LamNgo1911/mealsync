package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserInfoDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;

public interface IAuthService {
    UserReadDto register(UserCreateDto userCreateDto);
    UserInfoDto login(UserLoginDto userLoginDto);
    void logout(String token);
    UserInfoDto refreshToken (String token);
    boolean validateToken(String token);
    void changePassword(String email, String oldPassword, String newPassword);
}
