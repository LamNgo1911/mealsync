package com.lamngo.mealsync.application.dto.user;

import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDto {
    private String id;
    private String email;
    private String name;
    private UserRole role;
    private UserStatus status;
    private UserPreferenceReadDto userPreference;

    private String token;
    private RefreshTokenReadDto refreshToken;
}
