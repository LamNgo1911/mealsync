package com.lamngo.mealsync.application.mapper.recipe;

import com.lamngo.mealsync.application.dto.recipe.RecipeIngredientCreateDto;
import com.lamngo.mealsync.application.dto.recipe.RecipeIngredientReadDto;
import com.lamngo.mealsync.domain.model.recipe.RecipeIngredient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecipeIngredientMapper {

    RecipeIngredientReadDto toRecipeIngredientReadDto(RecipeIngredient recipeIngredient);

    List<RecipeIngredientReadDto> toRecipeIngredientReadDtoList(List<RecipeIngredient> recipeIngredients);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipe", ignore = true)
    RecipeIngredient toRecipeIngredient(RecipeIngredientCreateDto recipeIngredientCreateDto);

    List<RecipeIngredient> toRecipeIngredientList(List<RecipeIngredientCreateDto> recipeIngredientCreateDtos);

}
