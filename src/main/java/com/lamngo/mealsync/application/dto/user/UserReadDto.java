package com.lamngo.mealsync.application.dto.user;

import com.lamngo.mealsync.domain.model.user.UserRole;
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
    private UserPreferenceReadDto userPreference;
}
