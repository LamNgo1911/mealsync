package com.lamngo.mealsync.application.dto.user;

import com.lamngo.mealsync.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserReadDto {
    private String id;
    private String email;
    private String name;
    private Role role;
}
