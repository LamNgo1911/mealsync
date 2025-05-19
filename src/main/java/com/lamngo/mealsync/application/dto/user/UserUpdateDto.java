package com.lamngo.mealsync.application.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {
    @NotBlank(message = "Name cannot be blank if provided")
    private String name;

    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
