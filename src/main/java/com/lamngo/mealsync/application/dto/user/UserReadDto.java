package com.lamngo.mealsync.application.dto.user;

import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReadDto {
    private String id;
    private String email;
    private String name;
    private UserRole role;
    private UserStatus status;
    private UserPreferenceReadDto userPreference;
    private RefreshTokenReadDto refreshToken;
}
