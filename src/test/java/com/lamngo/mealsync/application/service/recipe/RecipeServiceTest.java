package com.lamngo.mealsync.application.service.recipe;

import com.lamngo.mealsync.application.dto.recipe.*;
import com.lamngo.mealsync.application.dto.userRecipe.UserRecipeReadDto;
import com.lamngo.mealsync.application.mapper.UserRecipeMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeIngredientMapper;
import com.lamngo.mealsync.application.mapper.recipe.RecipeMapper;
import com.lamngo.mealsync.application.service.AWS.S3Service;
import com.lamngo.mealsync.application.shared.OffsetPage;
import com.lamngo.mealsync.application.shared.PaginationResponse;
import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.UserRecipeType;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.IUserRecipeRepo;
import com.lamngo.mealsync.domain.repository.recipe.IRecipeRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipeServiceTest {
    private IRecipeRepo recipeRepo;
    private RecipeMapper recipeMapper;
    private IUserRecipeRepo userRecipeRepo;
    private UserRecipeMapper userRecipeMapper;
    private IUserRepo userRepo;
    private RecipeIngredientMapper recipeIngredientMapper;
    private RecipeRecommendationService recommendationService;
    private S3Service s3Service;
    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeRepo = mock(IRecipeRepo.class);
        recipeMapper = mock(RecipeMapper.class);
        userRecipeRepo = mock(IUserRecipeRepo.class);
        userRecipeMapper = mock(UserRecipeMapper.class);
        userRepo = mock(IUserRepo.class);
        recipeIngredientMapper = mock(RecipeIngredientMapper.class);
        recommendationService = mock(RecipeRecommendationService.class);
        s3Service = mock(S3Service.class);
        recipeService = new RecipeService(recipeRepo, recipeMapper, userRecipeRepo, userRecipeMapper, userRepo, recipeIngredientMapper, recommendationService, s3Service);
    }

    @Test
    void createRecipe_success() {
        RecipeIngredientReadDto ingredientDto = new RecipeIngredientReadDto();
        ingredientDto.setName("Egg");
        ingredientDto.setQuantity("2");
        ingredientDto.setUnit("pcs");
        RecipeCreateDto createDto = new RecipeCreateDto();
        createDto.setIngredients(List.of(ingredientDto));

        Recipe recipe = mock(Recipe.class);
        Recipe savedRecipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeMapper.toRecipe(createDto)).thenReturn(recipe);
        when(recipeRepo.createRecipe(recipe)).thenReturn(savedRecipe);
        when(recipeMapper.toRecipeReadDto(savedRecipe)).thenReturn(readDto);

        RecipeReadDto result = recipeService.createRecipe(createDto);
        assertEquals(readDto, result);
        verify(recipeRepo).createRecipe(recipe);
    }

    @Test
    void getRecipeById_found() {
        UUID id = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.of(recipe));
        when(recipeMapper.toRecipeReadDto(recipe)).thenReturn(readDto);
        RecipeReadDto result = recipeService.getRecipeById(id);
        assertEquals(readDto, result);
    }

    @Test
    void getRecipeById_notFound() {
        UUID id = UUID.randomUUID();
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.getRecipeById(id));
    }

    @Test
    void getAllRecipes_success() {
        int limit = 10, offset = 0;
        OffsetPage page = new OffsetPage(limit, offset);
        Page<Recipe> recipePage = mock(Page.class);
        Recipe recipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeRepo.getAllRecipes(any(OffsetPage.class))).thenReturn(recipePage);
        when(recipePage.getContent()).thenReturn(List.of(recipe));
        when(recipeMapper.toRecipeReadDto(recipe)).thenReturn(readDto);
        when(recipePage.getTotalElements()).thenReturn(1L);
        when(recipePage.hasNext()).thenReturn(false);
        PaginationResponse<RecipeReadDto> resp = recipeService.getAllRecipes(limit, offset);
        assertEquals(1, resp.getData().size());
        assertEquals(readDto, resp.getData().get(0));
    }

    @Test
    void updateRecipe_success() {
        UUID id = UUID.randomUUID();
        RecipeUpdateDto updateDto = mock(RecipeUpdateDto.class);
        Recipe recipe = mock(Recipe.class);
        Recipe updatedRecipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.of(recipe));
        doNothing().when(recipeMapper).updateRecipeFromDto(updateDto, recipe);
        when(recipeRepo.createRecipe(recipe)).thenReturn(updatedRecipe);
        when(recipeMapper.toRecipeReadDto(updatedRecipe)).thenReturn(readDto);
        RecipeReadDto result = recipeService.updateRecipe(id, updateDto);
        assertEquals(readDto, result);
    }

    @Test
    void updateRecipe_notFound() {
        UUID id = UUID.randomUUID();
        RecipeUpdateDto updateDto = mock(RecipeUpdateDto.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.updateRecipe(id, updateDto));
    }

    @Test
    void deleteRecipe_success() {
        UUID id = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        when(recipeRepo.getRecipeById(id)).thenReturn(Optional.of(recipe));
        when(recipe.getImageUrl()).thenReturn("https://s3.amazonaws.com/bucket/image.jpg");
        doNothing().when(s3Service).deleteImage(anyString());
        doNothing().when(recipeRepo).deleteRecipe(id);
        recipeService.deleteRecipe(id);
        verify(recipeRepo).deleteRecipe(id);
        verify(s3Service).deleteImage("https://s3.amazonaws.com/bucket/image.jpg");
    }

    @Test
    void addRecipeToUser_success() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        User user = mock(User.class);
        UserRecipe userRecipe = mock(UserRecipe.class);
        UserRecipeReadDto userRecipeReadDto = mock(UserRecipeReadDto.class);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipe));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRecipeRepo.saveUserRecipe(any())).thenReturn(userRecipe);
        when(userRecipeMapper.toUserRecipeReadDto(userRecipe)).thenReturn(userRecipeReadDto);
        UserRecipeReadDto result = recipeService.addRecipeToUser(userId, recipeId);
        assertEquals(userRecipeReadDto, result);
    }

    @Test
    void addRecipeToUser_recipeNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.addRecipeToUser(userId, recipeId));
    }

    @Test
    void addRecipeToUser_userNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = mock(Recipe.class);
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipe));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> recipeService.addRecipeToUser(userId, recipeId));
    }

    @Test
    void getTodayPicks_success() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        Recipe recipe1 = mock(Recipe.class);
        Recipe recipe2 = mock(Recipe.class);
        Recipe recipe3 = mock(Recipe.class);
        RecipeReadDto readDto1 = mock(RecipeReadDto.class);
        RecipeReadDto readDto2 = mock(RecipeReadDto.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recommendationService.getRecommendedRecipes(user, 20))
                .thenReturn(List.of(recipe1, recipe2, recipe3));
        when(recipeMapper.toRecipeReadDto(any(Recipe.class)))
                .thenReturn(readDto1, readDto2);

        List<RecipeReadDto> result = recipeService.getTodayPicks(userId);

        assertEquals(2, result.size());
        verify(userRepo).findById(userId);
        verify(recommendationService).getRecommendedRecipes(user, 20);
    }

    @Test
    void getTodayPicks_onlyOneRecipe() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        Recipe recipe = mock(Recipe.class);
        RecipeReadDto readDto = mock(RecipeReadDto.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recommendationService.getRecommendedRecipes(user, 20))
                .thenReturn(List.of(recipe));
        when(recipeMapper.toRecipeReadDto(recipe)).thenReturn(readDto);

        List<RecipeReadDto> result = recipeService.getTodayPicks(userId);

        assertEquals(1, result.size());
        assertEquals(readDto, result.get(0));
    }

    @Test
    void getTodayPicks_noRecommendations() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recommendationService.getRecommendedRecipes(user, 20))
                .thenReturn(Collections.emptyList());

        List<RecipeReadDto> result = recipeService.getTodayPicks(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getTodayPicks_userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> recipeService.getTodayPicks(userId));
    }

    @Test
    void addGeneratedRecipesToUser_success() {
        UUID userId = UUID.randomUUID();
        UUID recipeId1 = UUID.randomUUID();
        UUID recipeId2 = UUID.randomUUID();
        List<UUID> recipeIds = List.of(recipeId1, recipeId2);

        User user = mock(User.class);
        Recipe recipe1 = mock(Recipe.class);
        Recipe recipe2 = mock(Recipe.class);
        UserRecipe userRecipe1 = mock(UserRecipe.class);
        UserRecipe userRecipe2 = mock(UserRecipe.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recipeRepo.getRecipeById(recipeId1)).thenReturn(Optional.of(recipe1));
        when(recipeRepo.getRecipeById(recipeId2)).thenReturn(Optional.of(recipe2));
        when(userRecipeRepo.saveUserRecipe(any(UserRecipe.class)))
                .thenReturn(userRecipe1)
                .thenReturn(userRecipe2);

        recipeService.addGeneratedRecipesToUser(userId, recipeIds);

        verify(userRepo).findById(userId);
        verify(recipeRepo).getRecipeById(recipeId1);
        verify(recipeRepo).getRecipeById(recipeId2);
        verify(userRecipeRepo, times(2)).saveUserRecipe(any(UserRecipe.class));
    }

    @Test
    void addGeneratedRecipesToUser_userNotFound() {
        UUID userId = UUID.randomUUID();
        List<UUID> recipeIds = List.of(UUID.randomUUID());

        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recipeService.addGeneratedRecipesToUser(userId, recipeIds));
        verify(recipeRepo, never()).getRecipeById(any());
        verify(userRecipeRepo, never()).saveUserRecipe(any());
    }

    @Test
    void addGeneratedRecipesToUser_recipeNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        List<UUID> recipeIds = List.of(recipeId);

        User user = mock(User.class);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recipeService.addGeneratedRecipesToUser(userId, recipeIds));
        verify(userRecipeRepo, never()).saveUserRecipe(any());
    }

    @Test
    void getRecentGeneratedRecipes_success() {
        UUID userId = UUID.randomUUID();
        int limit = 5;

        Recipe recipe1 = mock(Recipe.class);
        Recipe recipe2 = mock(Recipe.class);
        Recipe recipe3 = mock(Recipe.class);

        UserRecipe userRecipe1 = new UserRecipe();
        userRecipe1.setRecipe(recipe1);
        userRecipe1.setType(UserRecipeType.GENERATED);
        userRecipe1.setSavedAt(LocalDateTime.now().minusHours(1));

        UserRecipe userRecipe2 = new UserRecipe();
        userRecipe2.setRecipe(recipe2);
        userRecipe2.setType(UserRecipeType.GENERATED);
        userRecipe2.setSavedAt(LocalDateTime.now().minusHours(2));

        UserRecipe userRecipe3 = new UserRecipe();
        userRecipe3.setRecipe(recipe3);
        userRecipe3.setType(UserRecipeType.SAVED);
        userRecipe3.setSavedAt(LocalDateTime.now().minusMinutes(30));

        List<UserRecipe> userRecipes = List.of(userRecipe1, userRecipe2, userRecipe3);

        RecipeReadDto readDto1 = mock(RecipeReadDto.class);
        RecipeReadDto readDto2 = mock(RecipeReadDto.class);

        when(userRecipeRepo.getUserRecipesByUserId(userId)).thenReturn(userRecipes);
        when(recipeMapper.toRecipeReadDto(recipe1)).thenReturn(readDto1);
        when(recipeMapper.toRecipeReadDto(recipe2)).thenReturn(readDto2);

        List<RecipeReadDto> result = recipeService.getRecentGeneratedRecipes(userId, limit);

        // Should only return GENERATED type recipes, sorted by most recent first
        assertEquals(2, result.size());
        assertEquals(readDto1, result.get(0)); // Most recent
        assertEquals(readDto2, result.get(1));
        verify(userRecipeRepo).getUserRecipesByUserId(userId);
    }

    @Test
    void getRecentGeneratedRecipes_emptyList() {
        UUID userId = UUID.randomUUID();
        int limit = 10;

        when(userRecipeRepo.getUserRecipesByUserId(userId)).thenReturn(Collections.emptyList());

        List<RecipeReadDto> result = recipeService.getRecentGeneratedRecipes(userId, limit);

        assertTrue(result.isEmpty());
        verify(userRecipeRepo).getUserRecipesByUserId(userId);
    }

    @Test
    void getRecentGeneratedRecipes_onlySavedRecipes() {
        UUID userId = UUID.randomUUID();
        int limit = 10;

        Recipe recipe = mock(Recipe.class);
        UserRecipe userRecipe = new UserRecipe();
        userRecipe.setRecipe(recipe);
        userRecipe.setType(UserRecipeType.SAVED);
        userRecipe.setSavedAt(LocalDateTime.now());

        when(userRecipeRepo.getUserRecipesByUserId(userId)).thenReturn(List.of(userRecipe));

        List<RecipeReadDto> result = recipeService.getRecentGeneratedRecipes(userId, limit);

        assertTrue(result.isEmpty());
        verify(userRecipeRepo).getUserRecipesByUserId(userId);
    }

    @Test
    void getRecentGeneratedRecipes_respectsLimit() {
        UUID userId = UUID.randomUUID();
        int limit = 2;

        List<UserRecipe> userRecipes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Recipe recipe = mock(Recipe.class);
            UserRecipe userRecipe = new UserRecipe();
            userRecipe.setRecipe(recipe);
            userRecipe.setType(UserRecipeType.GENERATED);
            userRecipe.setSavedAt(LocalDateTime.now().minusHours(i));
            userRecipes.add(userRecipe);
        }

        when(userRecipeRepo.getUserRecipesByUserId(userId)).thenReturn(userRecipes);
        when(recipeMapper.toRecipeReadDto(any(Recipe.class)))
                .thenReturn(mock(RecipeReadDto.class));

        List<RecipeReadDto> result = recipeService.getRecentGeneratedRecipes(userId, limit);

        assertEquals(2, result.size());
        verify(userRecipeRepo).getUserRecipesByUserId(userId);
    }

    @Test
    void removeRecipeFromUser_success() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        UUID userRecipeId = UUID.randomUUID();
        User user = mock(User.class);
        Recipe recipe = mock(Recipe.class);
        UserRecipe userRecipe = mock(UserRecipe.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipe));
        when(userRecipeRepo.getUserRecipeByUserIdAndRecipeIdAndType(userId, recipeId, UserRecipeType.SAVED))
                .thenReturn(Optional.of(userRecipe));
        when(userRecipe.getId()).thenReturn(userRecipeId);
        doNothing().when(userRecipeRepo).deleteUserRecipe(userRecipeId);

        recipeService.removeRecipeFromUser(userId, recipeId);

        verify(userRepo).findById(userId);
        verify(recipeRepo).getRecipeById(recipeId);
        verify(userRecipeRepo).getUserRecipeByUserIdAndRecipeIdAndType(userId, recipeId, UserRecipeType.SAVED);
        verify(userRecipeRepo).deleteUserRecipe(userRecipeId);
    }

    @Test
    void removeRecipeFromUser_userNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();

        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recipeService.removeRecipeFromUser(userId, recipeId));
        verify(recipeRepo, never()).getRecipeById(any());
        verify(userRecipeRepo, never()).getUserRecipeByUserIdAndRecipeIdAndType(any(), any(), any());
        verify(userRecipeRepo, never()).deleteUserRecipe(any());
    }

    @Test
    void removeRecipeFromUser_recipeNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recipeService.removeRecipeFromUser(userId, recipeId));
        verify(userRecipeRepo, never()).getUserRecipeByUserIdAndRecipeIdAndType(any(), any(), any());
        verify(userRecipeRepo, never()).deleteUserRecipe(any());
    }

    @Test
    void removeRecipeFromUser_savedRecipeNotFound() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        User user = mock(User.class);
        Recipe recipe = mock(Recipe.class);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(recipeRepo.getRecipeById(recipeId)).thenReturn(Optional.of(recipe));
        when(userRecipeRepo.getUserRecipeByUserIdAndRecipeIdAndType(userId, recipeId, UserRecipeType.SAVED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recipeService.removeRecipeFromUser(userId, recipeId));
        verify(userRecipeRepo, never()).deleteUserRecipe(any());
    }

    @Test
    void getSavedRecipesByUserId_success() {
        UUID userId = UUID.randomUUID();
        int limit = 5;

        Recipe recipe1 = mock(Recipe.class);
        Recipe recipe2 = mock(Recipe.class);

        UserRecipe userRecipe1 = mock(UserRecipe.class);
        when(userRecipe1.getRecipe()).thenReturn(recipe1);

        UserRecipe userRecipe2 = mock(UserRecipe.class);
        when(userRecipe2.getRecipe()).thenReturn(recipe2);

        List<UserRecipe> savedRecipes = List.of(userRecipe1, userRecipe2);
        UserRecipeReadDto readDto1 = mock(UserRecipeReadDto.class);
        UserRecipeReadDto readDto2 = mock(UserRecipeReadDto.class);

        when(userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED))
                .thenReturn(savedRecipes);
        when(userRecipeMapper.toUserRecipeReadDto(userRecipe1)).thenReturn(readDto1);
        when(userRecipeMapper.toUserRecipeReadDto(userRecipe2)).thenReturn(readDto2);

        List<UserRecipeReadDto> result = recipeService.getSavedRecipesByUserId(userId, limit);

        assertEquals(2, result.size());
        assertEquals(readDto1, result.get(0));
        assertEquals(readDto2, result.get(1));
        verify(userRecipeRepo).getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED);
    }

    @Test
    void getSavedRecipesByUserId_respectsLimit() {
        UUID userId = UUID.randomUUID();
        int limit = 2;

        List<UserRecipe> savedRecipes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UserRecipe userRecipe = mock(UserRecipe.class);
            Recipe recipe = mock(Recipe.class);
            when(userRecipe.getRecipe()).thenReturn(recipe);
            savedRecipes.add(userRecipe);
        }

        when(userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED))
                .thenReturn(savedRecipes);
        when(userRecipeMapper.toUserRecipeReadDto(any(UserRecipe.class)))
                .thenReturn(mock(UserRecipeReadDto.class));

        List<UserRecipeReadDto> result = recipeService.getSavedRecipesByUserId(userId, limit);

        assertEquals(2, result.size());
        verify(userRecipeRepo).getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED);
    }

    @Test
    void getSavedRecipesByUserId_emptyList() {
        UUID userId = UUID.randomUUID();
        int limit = 10;

        when(userRecipeRepo.getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED))
                .thenReturn(Collections.emptyList());

        List<UserRecipeReadDto> result = recipeService.getSavedRecipesByUserId(userId, limit);

        assertTrue(result.isEmpty());
        verify(userRecipeRepo).getUserRecipesByUserIdAndType(userId, UserRecipeType.SAVED);
    }
}
