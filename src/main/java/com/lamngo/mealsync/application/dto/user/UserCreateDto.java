package com.lamngo.mealsync.application.dto.user;

import com.lamngo.mealsync.domain.model.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor; // Added
import lombok.Builder;         // Added
import lombok.Data;            // Added (includes @Getter, @Setter, @ToString, @EqualsAndHashCode)
import lombok.NoArgsConstructor;  // Added

@Data // Replaces @Getter and @Setter, adds more
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateDto {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Role is required")
    private UserRole role;
}