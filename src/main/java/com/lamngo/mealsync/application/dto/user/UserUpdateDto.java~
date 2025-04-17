package com.lamngo.mealsync.application.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {
    private String name;

    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
