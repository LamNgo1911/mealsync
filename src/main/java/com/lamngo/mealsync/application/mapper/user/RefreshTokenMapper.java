package com.lamngo.mealsync.application.mapper.user;

import com.lamngo.mealsync.application.dto.user.RefreshTokenReadDto;
import com.lamngo.mealsync.domain.model.user.RefreshToken;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefreshTokenMapper {
    RefreshTokenReadDto toRefreshTokenReadDto(RefreshToken refreshToken);
}
