package com.lamngo.mealsync.application.mapper.user;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserUpdateDto;
import com.lamngo.mealsync.domain.model.user.User;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = { UserPreferenceMapper.class }
)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userPreference", ignore = true)
    @Mapping(target = "mealPlans", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "accountNonExpired", ignore = true)
    @Mapping(target = "accountNonLocked", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "username", ignore = true)
    User toUser(UserCreateDto userCreateDto);

    UserReadDto toUserReadDto(User user);

    List<UserReadDto> toUserReadDtoList(List<User> users);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "userPreference", ignore = true)
    @Mapping(target = "mealPlans", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "accountNonExpired", ignore = true)
    @Mapping(target = "accountNonLocked", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "username", ignore = true)
    void updateUserFromDto(UserUpdateDto userUpdateDto, @MappingTarget User user);
}