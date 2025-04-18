package com.lamngo.mealsync.application.mapper.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeReadDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeUpdateDto;
import com.lamngo.mealsync.domain.model.recipe.Recipe; // Assuming this is the correct path for your domain model
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = { RecipeIngredientMapper.class }
)
public interface RecipeMapper {

    RecipeReadDto toRecipeReadDto(Recipe recipe);

    List<RecipeReadDto> toRecipeReadDtoList(List<Recipe> recipes);

    @Mapping(target = "id", ignore = true)
    Recipe toRecipe(RecipeCreateDto recipeCreateDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateRecipeFromDto(RecipeUpdateDto recipeUpdateDto, @MappingTarget Recipe recipe);

}