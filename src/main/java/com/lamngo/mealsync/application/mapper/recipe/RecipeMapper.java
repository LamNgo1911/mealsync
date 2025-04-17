package com.lamngo.mealsync.application.mapper.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.domain.model.recipe.Recipe; // Assuming this is the correct path for your domain model
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecipeMapper {

    RecipeMapper INSTANCE = Mappers.getMapper(RecipeMapper.class);

    RecipeReadDto toRecipeReadDto(Recipe recipe);

    List<RecipeReadDto> toRecipeReadDtoList(List<Recipe> recipes);

    Recipe toRecipe(RecipeCreateDto recipeCreateDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateRecipeFromDto(RecipeUpdateDto recipeUpdateDto, @MappingTarget Recipe recipe);

}