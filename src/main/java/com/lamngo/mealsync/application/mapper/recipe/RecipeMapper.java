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

    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "cuisine", source = "cuisine")
    @Mapping(target = "preparationTime", source = "preparationTime")
    @Mapping(target = "cookingTime", source = "cookingTime")
    @Mapping(target = "totalTime", source = "totalTime")
    @Mapping(target = "servings", source = "servings")
    @Mapping(target = "calories", source = "calories")
    @Mapping(target = "protein", source = "protein")
    @Mapping(target = "carbohydrates", source = "carbohydrates")
    @Mapping(target = "fat", source = "fat")
    @Mapping(target = "difficulty", source = "difficulty")
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "ingredients", source = "ingredients")
    RecipeReadDto toRecipeReadDto(Recipe recipe);

    List<RecipeReadDto> toRecipeReadDtoList(List<Recipe> recipes);

    @Mapping(target = "id", ignore = true)
    Recipe toRecipe(RecipeCreateDto recipeCreateDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateRecipeFromDto(RecipeUpdateDto recipeUpdateDto, @MappingTarget Recipe recipe);

}