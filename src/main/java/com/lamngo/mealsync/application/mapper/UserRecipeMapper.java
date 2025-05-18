package com.lamngo.mealsync.application.mapper;

import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeCreateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.domain.model.UserRecipe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserRecipeMapper {

    UserRecipeReadDto toUserRecipeReadDto(UserRecipe userRecipe);

    List<UserRecipeReadDto> toUserRecipeReadDtoList(List<UserRecipe> userRecipes);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "recipe", ignore = true)
    UserRecipe toUserRecipe(UserRecipeCreateDto dto);

}
