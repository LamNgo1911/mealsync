package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.recipe.DetectedIngredientDto;
import com.lamngo.mealsync.application.dto.recipe.*;
import com.lamngo.mealsync.application.dto.user.UserPreferenceRequestDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeCreateDto;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.service.AI.AIRecipeService;
import com.lamngo.mealsync.application.service.AI.IngredientDetectionService;
import com.lamngo.mealsync.application.service.recipe.RecipeService;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipeControllerUnitTest {
    @Mock RecipeService recipeService;
    @Mock AIRecipeService aiRecipeService;
    @Mock com.lamngo.mealsync.application.service.recipe.RecipeGenerationOrchestrator recipeGenerationOrchestrator;
    @Mock com.lamngo.mealsync.application.service.recipe.RecipeImageStreamingService recipeImageStreamingService;
    @Mock IngredientDetectionService ingredientDetectionService;
    @InjectMocks RecipeController controller;

    @BeforeEach
    void setup() { MockitoAnnotations.openMocks(this); }

    @Test
    void generateRecipesFromIngredients_success() {
        GenerateRecipeRequest request = new GenerateRecipeRequest();
        DetectedIngredientDto egg = new DetectedIngredientDto();
        egg.setName("egg");
        egg.setQuantity("2");
        egg.setUnit("pieces");
        DetectedIngredientDto milk = new DetectedIngredientDto();
        milk.setName("milk");
        milk.setQuantity("200");
        milk.setUnit("ml");
        request.setIngredients(List.of(egg, milk));
        UserPreferenceRequestDto userPreferenceDto = new UserPreferenceRequestDto();
        userPreferenceDto.setDietaryRestrictions(List.of());
        userPreferenceDto.setFavoriteCuisines(List.of());
        userPreferenceDto.setDislikedIngredients(List.of());
        request.setUserPreference(userPreferenceDto);

        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(UUID.randomUUID());
        List<RecipeReadDto> recipes = List.of(recipe);

        when(recipeGenerationOrchestrator.generateRecipesFromIngredients(eq(request.getIngredients()), any(UserPreference.class))).thenReturn(recipes);
        doNothing().when(recipeGenerationOrchestrator).saveGeneratedRecipesToUserAsync(eq(userId), anyList());

        ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> resp = controller.generateRecipes(request, user);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(recipes, resp.getBody().getData());
        verify(recipeGenerationOrchestrator).generateRecipesFromIngredients(eq(request.getIngredients()), any(UserPreference.class));
        verify(recipeGenerationOrchestrator).saveGeneratedRecipesToUserAsync(eq(userId), anyList());
        verify(ingredientDetectionService, never()).detectRawIngredientsAsync(any());
    }

    @Test
    void saveRecipeWithUser_success() {
        UserRecipeCreateDto dto = new UserRecipeCreateDto();
        dto.setUserId(UUID.randomUUID());
        dto.setRecipeId(UUID.randomUUID());
        UserRecipeReadDto readDto = new UserRecipeReadDto();
        when(recipeService.addRecipeToUser(any(), any())).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<UserRecipeReadDto>> resp = controller.saveRecipeWithUser(dto);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void unsaveRecipeFromUser_success() {
        UserRecipeCreateDto dto = new UserRecipeCreateDto();
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        dto.setUserId(userId);
        dto.setRecipeId(recipeId);

        doNothing().when(recipeService).removeRecipeFromUser(userId, recipeId);

        ResponseEntity<Void> resp = controller.unsaveRecipeFromUser(dto);

        assertEquals(204, resp.getStatusCodeValue());
        verify(recipeService).removeRecipeFromUser(userId, recipeId);
    }

    @Test
    void getSavedRecipes_success() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        UserRecipeReadDto savedRecipe1 = new UserRecipeReadDto();
        savedRecipe1.setId(UUID.randomUUID());

        UserRecipeReadDto savedRecipe2 = new UserRecipeReadDto();
        savedRecipe2.setId(UUID.randomUUID());

        PaginationResponse<UserRecipeReadDto> paginationResponse = PaginationResponse.<UserRecipeReadDto>builder()
                .data(List.of(savedRecipe1, savedRecipe2))
                .offset(0)
                .limit(6)
                .totalElements(2L)
                .hasNext(false)
                .build();
        when(recipeService.getSavedRecipesByUserId(userId, 6, 0)).thenReturn(paginationResponse);

        ResponseEntity<PaginationResponse<UserRecipeReadDto>> resp =
                controller.getSavedRecipes(user, 0, 6);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().getData().size());
        assertEquals(0, resp.getBody().getOffset());
        assertEquals(6, resp.getBody().getLimit());
        assertEquals(2L, resp.getBody().getTotalElements());
        assertEquals(false, resp.getBody().isHasNext());
        verify(recipeService).getSavedRecipesByUserId(userId, 6, 0);
    }

    @Test
    void getSavedRecipes_emptyResult() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        PaginationResponse<UserRecipeReadDto> paginationResponse = PaginationResponse.<UserRecipeReadDto>builder()
                .data(List.of())
                .offset(0)
                .limit(6)
                .totalElements(0L)
                .hasNext(false)
                .build();
        when(recipeService.getSavedRecipesByUserId(userId, 6, 0)).thenReturn(paginationResponse);

        ResponseEntity<PaginationResponse<UserRecipeReadDto>> resp =
                controller.getSavedRecipes(user, 0, 6);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().getData().isEmpty());
        assertEquals(0L, resp.getBody().getTotalElements());
        verify(recipeService).getSavedRecipesByUserId(userId, 6, 0);
    }

    @Test
    void createRecipe_success() {
        RecipeCreateDto dto = new RecipeCreateDto();
        RecipeReadDto readDto = new RecipeReadDto();
        when(recipeService.createRecipe(any())).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<RecipeReadDto>> resp = controller.createRecipe(dto);
        assertEquals(201, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void getRecipeById_success() {
        UUID id = UUID.randomUUID();
        RecipeReadDto readDto = new RecipeReadDto();
        when(recipeService.getRecipeById(id)).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<RecipeReadDto>> resp = controller.getRecipeById(id.toString());
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void getAllRecipes_success() {
        PaginationResponse<RecipeReadDto> page = PaginationResponse.<RecipeReadDto>builder()
                .data(List.of(new RecipeReadDto()))
                .offset(0)
                .limit(2)
                .totalElements(1L)
                .hasNext(false)
                .build();
        when(recipeService.getAllRecipes(anyInt(), anyInt())).thenReturn(page);
        ResponseEntity<PaginationResponse<RecipeReadDto>> resp = controller.getAllRecipes(0, 2,null, null, null, null, null, null, null);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(page, resp.getBody());
    }

    @Test
    void updateRecipe_success() {
        UUID id = UUID.randomUUID();
        RecipeUpdateDto updateDto = new RecipeUpdateDto();
        RecipeReadDto readDto = new RecipeReadDto();
        when(recipeService.updateRecipe(eq(id), any())).thenReturn(readDto);
        ResponseEntity<SuccessResponseEntity<RecipeReadDto>> resp = controller.updateRecipe(id.toString(), updateDto);
        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(readDto, resp.getBody().getData());
    }

    @Test
    void deleteRecipe_success() {
        UUID id = UUID.randomUUID();
        doNothing().when(recipeService).deleteRecipe(id);
        ResponseEntity<Void> resp = controller.deleteRecipe(id.toString());
        assertEquals(204, resp.getStatusCodeValue());
        verify(recipeService).deleteRecipe(id);
    }

    @Test
    void detectIngredients_success() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        DetectedIngredientDto tomato = new DetectedIngredientDto();
        tomato.setName("tomato");
        tomato.setQuantity("2");
        tomato.setUnit("pieces");
        DetectedIngredientDto onion = new DetectedIngredientDto();
        onion.setName("onion");
        onion.setQuantity("1");
        onion.setUnit("piece");
        DetectedIngredientDto garlic = new DetectedIngredientDto();
        garlic.setName("garlic");
        garlic.setQuantity("3");
        garlic.setUnit("cloves");
        DetectedIngredientDto chicken = new DetectedIngredientDto();
        chicken.setName("chicken");
        chicken.setQuantity("200");
        chicken.setUnit("grams");
        List<DetectedIngredientDto> ingredients = List.of(tomato, onion, garlic, chicken);
        java.util.concurrent.CompletableFuture<List<DetectedIngredientDto>> future = 
            java.util.concurrent.CompletableFuture.completedFuture(ingredients);
        when(ingredientDetectionService.detectRawIngredientsAsync(image)).thenReturn(future);

        ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> resp = controller.detectIngredients(image);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(ingredients, resp.getBody().getData());
        assertEquals(4, resp.getBody().getData().size());
        verify(ingredientDetectionService).detectRawIngredientsAsync(image);
    }

    @Test
    void detectIngredients_emptyImage_throwsBadRequestException() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(true);

        assertThrows(BadRequestException.class, () -> controller.detectIngredients(image));
        verify(ingredientDetectionService, never()).detectRawIngredientsAsync(any());
    }

    @Test
    void detectIngredients_nullImage_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () -> controller.detectIngredients(null));
        verify(ingredientDetectionService, never()).detectRawIngredients(any());
    }

    @Test
    void detectIngredients_emptyResults() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        List<DetectedIngredientDto> emptyIngredients = List.of();
        java.util.concurrent.CompletableFuture<List<DetectedIngredientDto>> future = 
            java.util.concurrent.CompletableFuture.completedFuture(emptyIngredients);
        when(ingredientDetectionService.detectRawIngredientsAsync(image)).thenReturn(future);

        ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> resp = controller.detectIngredients(image);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().getData().isEmpty());
        verify(ingredientDetectionService).detectRawIngredientsAsync(image);
    }

    @Test
    void validateAndParseIngredientsFromText_success() {
        DetectedIngredientDto tomato = new DetectedIngredientDto();
        tomato.setName("tomato");
        tomato.setQuantity("2");
        tomato.setUnit("pieces");
        DetectedIngredientDto onion = new DetectedIngredientDto();
        onion.setName("onion");
        onion.setQuantity("1");
        onion.setUnit("piece");
        DetectedIngredientDto garlic = new DetectedIngredientDto();
        garlic.setName("garlic");
        garlic.setQuantity("3");
        garlic.setUnit("cloves");
        DetectedIngredientDto chicken = new DetectedIngredientDto();
        chicken.setName("chicken breast");
        chicken.setQuantity("200");
        chicken.setUnit("grams");

        List<DetectedIngredientDto> inputIngredients = List.of(tomato, onion, garlic, chicken);
        ManualIngredientDetectionRequest request = new ManualIngredientDetectionRequest();
        request.setIngredients(inputIngredients);

        List<DetectedIngredientDto> validatedIngredients = List.of(tomato, onion, garlic, chicken);
        when(ingredientDetectionService.validateAndParseIngredientsFromText(inputIngredients)).thenReturn(validatedIngredients);

        ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> resp = 
                controller.validateAndParseIngredientsFromText(request);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(validatedIngredients, resp.getBody().getData());
        assertEquals(4, resp.getBody().getData().size());
        verify(ingredientDetectionService).validateAndParseIngredientsFromText(inputIngredients);
    }

    @Test
    void validateAndParseIngredientsFromText_emptyList_throwsBadRequestException() {
        ManualIngredientDetectionRequest request = new ManualIngredientDetectionRequest();
        request.setIngredients(List.of());

        assertThrows(BadRequestException.class, () -> controller.validateAndParseIngredientsFromText(request));
        verify(ingredientDetectionService, never()).validateAndParseIngredientsFromText(any());
    }

    @Test
    void validateAndParseIngredientsFromText_nullList_throwsBadRequestException() {
        ManualIngredientDetectionRequest request = new ManualIngredientDetectionRequest();
        request.setIngredients(null);

        assertThrows(BadRequestException.class, () -> controller.validateAndParseIngredientsFromText(request));
        verify(ingredientDetectionService, never()).validateAndParseIngredientsFromText(any());
    }

    @Test
    void validateAndParseIngredientsFromText_emptyResults() {
        DetectedIngredientDto invalidIngredient = new DetectedIngredientDto();
        invalidIngredient.setName("xyz123");
        invalidIngredient.setQuantity("1");
        invalidIngredient.setUnit("");

        List<DetectedIngredientDto> inputIngredients = List.of(invalidIngredient);
        ManualIngredientDetectionRequest request = new ManualIngredientDetectionRequest();
        request.setIngredients(inputIngredients);

        List<DetectedIngredientDto> emptyIngredients = List.of();
        when(ingredientDetectionService.validateAndParseIngredientsFromText(inputIngredients)).thenReturn(emptyIngredients);

        ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> resp = 
                controller.validateAndParseIngredientsFromText(request);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().getData().isEmpty());
        verify(ingredientDetectionService).validateAndParseIngredientsFromText(inputIngredients);
    }

    @Test
    void validateAndParseIngredientsFromText_singleIngredient() {
        DetectedIngredientDto beef = new DetectedIngredientDto();
        beef.setName("beef");
        beef.setQuantity("200");
        beef.setUnit("grams");

        List<DetectedIngredientDto> inputIngredients = List.of(beef);
        ManualIngredientDetectionRequest request = new ManualIngredientDetectionRequest();
        request.setIngredients(inputIngredients);

        List<DetectedIngredientDto> validatedIngredients = List.of(beef);
        when(ingredientDetectionService.validateAndParseIngredientsFromText(inputIngredients)).thenReturn(validatedIngredients);

        ResponseEntity<SuccessResponseEntity<List<DetectedIngredientDto>>> resp = 
                controller.validateAndParseIngredientsFromText(request);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().getData().size());
        assertEquals(beef, resp.getBody().getData().get(0));
        verify(ingredientDetectionService).validateAndParseIngredientsFromText(inputIngredients);
    }

    @Test
    void getTodayPicks_success() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        RecipeReadDto recipe1 = new RecipeReadDto();
        recipe1.setId(UUID.randomUUID());
        recipe1.setName("Recipe 1");

        RecipeReadDto recipe2 = new RecipeReadDto();
        recipe2.setId(UUID.randomUUID());
        recipe2.setName("Recipe 2");

        List<RecipeReadDto> todayPicks = List.of(recipe1, recipe2);
        when(recipeService.getTodayPicks(userId)).thenReturn(todayPicks);

        ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> resp = controller.getTodayPicks(user);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(todayPicks, resp.getBody().getData());
        assertEquals(2, resp.getBody().getData().size());
        verify(recipeService).getTodayPicks(userId);
    }

    @Test
    void getTodayPicks_emptyResult() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        List<RecipeReadDto> emptyPicks = List.of();
        when(recipeService.getTodayPicks(userId)).thenReturn(emptyPicks);

        ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> resp = controller.getTodayPicks(user);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().getData().isEmpty());
        verify(recipeService).getTodayPicks(userId);
    }

    @Test
    void getTodayPicks_singleRecipe() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        RecipeReadDto recipe = new RecipeReadDto();
        recipe.setId(UUID.randomUUID());
        recipe.setName("Single Recipe");

        List<RecipeReadDto> todayPicks = List.of(recipe);
        when(recipeService.getTodayPicks(userId)).thenReturn(todayPicks);

        ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> resp = controller.getTodayPicks(user);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().getData().size());
        assertEquals(recipe, resp.getBody().getData().get(0));
        verify(recipeService).getTodayPicks(userId);
    }

    @Test
    void getRecentGeneratedRecipes_success() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        RecipeReadDto recipe1 = new RecipeReadDto();
        recipe1.setId(UUID.randomUUID());
        recipe1.setName("Recent Recipe 1");

        RecipeReadDto recipe2 = new RecipeReadDto();
        recipe2.setId(UUID.randomUUID());
        recipe2.setName("Recent Recipe 2");

        PaginationResponse<RecipeReadDto> paginationResponse = PaginationResponse.<RecipeReadDto>builder()
                .data(List.of(recipe1, recipe2))
                .offset(0)
                .limit(6)
                .totalElements(2L)
                .hasNext(false)
                .build();
        when(recipeService.getRecentGeneratedRecipes(userId, 6, 0)).thenReturn(paginationResponse);

        ResponseEntity<PaginationResponse<RecipeReadDto>> resp =
                controller.getRecentGeneratedRecipes(user, 0, 6);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().getData().size());
        assertEquals(0, resp.getBody().getOffset());
        assertEquals(6, resp.getBody().getLimit());
        assertEquals(2L, resp.getBody().getTotalElements());
        assertEquals(false, resp.getBody().isHasNext());
        verify(recipeService).getRecentGeneratedRecipes(userId, 6, 0);
    }

    @Test
    void getRecentGeneratedRecipes_emptyResult() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        PaginationResponse<RecipeReadDto> paginationResponse = PaginationResponse.<RecipeReadDto>builder()
                .data(List.of())
                .offset(0)
                .limit(6)
                .totalElements(0L)
                .hasNext(false)
                .build();
        when(recipeService.getRecentGeneratedRecipes(userId, 6, 0)).thenReturn(paginationResponse);

        ResponseEntity<PaginationResponse<RecipeReadDto>> resp =
                controller.getRecentGeneratedRecipes(user, 0, 6);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().getData().isEmpty());
        assertEquals(0L, resp.getBody().getTotalElements());
        verify(recipeService).getRecentGeneratedRecipes(userId, 6, 0);
    }

    @Test
    void getRecentGeneratedRecipes_withCustomLimit() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        PaginationResponse<RecipeReadDto> paginationResponse = PaginationResponse.<RecipeReadDto>builder()
                .data(List.of(new RecipeReadDto(), new RecipeReadDto()))
                .offset(0)
                .limit(20)
                .totalElements(2L)
                .hasNext(false)
                .build();
        when(recipeService.getRecentGeneratedRecipes(userId, 20, 0)).thenReturn(paginationResponse);

        ResponseEntity<PaginationResponse<RecipeReadDto>> resp =
                controller.getRecentGeneratedRecipes(user, 0, 20);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().getData().size());
        assertEquals(20, resp.getBody().getLimit());
        verify(recipeService).getRecentGeneratedRecipes(userId, 20, 0);
    }

    @Test
    void generateRecipes_savesGeneratedRecipes() {
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);

        GenerateRecipeRequest request = new GenerateRecipeRequest();
        DetectedIngredientDto chicken = new DetectedIngredientDto();
        chicken.setName("chicken");
        chicken.setQuantity("300");
        chicken.setUnit("grams");
        DetectedIngredientDto rice = new DetectedIngredientDto();
        rice.setName("rice");
        rice.setQuantity("200");
        rice.setUnit("grams");
        DetectedIngredientDto tomato = new DetectedIngredientDto();
        tomato.setName("tomato");
        tomato.setQuantity("2");
        tomato.setUnit("pieces");
        request.setIngredients(List.of(chicken, rice, tomato));
        UserPreferenceRequestDto userPreferenceDto = new UserPreferenceRequestDto();
        userPreferenceDto.setDietaryRestrictions(List.of("vegetarian"));
        userPreferenceDto.setFavoriteCuisines(List.of("Italian"));
        userPreferenceDto.setDislikedIngredients(List.of("mushroom"));
        request.setUserPreference(userPreferenceDto);

        RecipeReadDto recipe1 = new RecipeReadDto();
        recipe1.setId(UUID.randomUUID());
        RecipeReadDto recipe2 = new RecipeReadDto();
        recipe2.setId(UUID.randomUUID());

        List<RecipeReadDto> recipes = List.of(recipe1, recipe2);
        when(recipeGenerationOrchestrator.generateRecipesFromIngredients(eq(request.getIngredients()), any(UserPreference.class)))
                .thenReturn(recipes);
        doNothing().when(recipeGenerationOrchestrator).saveGeneratedRecipesToUserAsync(eq(userId), anyList());

        ResponseEntity<SuccessResponseEntity<List<RecipeReadDto>>> resp =
                controller.generateRecipes(request, user);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertEquals(recipes, resp.getBody().getData());
        verify(recipeGenerationOrchestrator).generateRecipesFromIngredients(eq(request.getIngredients()), any(UserPreference.class));
        verify(recipeGenerationOrchestrator).saveGeneratedRecipesToUserAsync(eq(userId), anyList());
    }
}
