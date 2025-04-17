package com.lamngo.mealsync.application.mapper.user;

import com.lamngo.mealsync.application.dto.user.UserPreferenceReadDto;
import com.lamngo.mealsync.application.dto.user.UserPreferenceUpdateDto;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserPreferenceMapper {

    UserPreferenceReadDto toUserPreferenceReadDto(UserPreference userPreference);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateUserPreferenceFromDto(UserPreferenceUpdateDto dto, @MappingTarget UserPreference entity);

}
