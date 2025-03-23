package com.lamngo.mealsync.application.mapper.user;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserUpdateDto;
import com.lamngo.mealsync.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring", // Integrate with Spring IoC
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE // Ignore null fields in updates
)
public interface UserMapper {
    User toUser(UserCreateDto userCreateDto);

    UserReadDto toUserReadDto(User user);

    void updateUserFromDto(UserUpdateDto userUpdateDto, @MappingTarget User user);
}
