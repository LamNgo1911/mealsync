package com.lamngo.mealsync.presentation.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.application.service.auth.AuthService;
import com.lamngo.mealsync.application.service.auth.GoogleVerifierService;
import com.lamngo.mealsync.application.service.auth.RefreshTokenService;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerUnitTest {
    @Mock private AuthService authService;
    @Mock private GoogleVerifierService googleVerifierService;
    @Mock private IUserRepo userRepo;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserMapper userMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_success() {
        UserCreateDto createDto = new UserCreateDto();
        createDto.setEmail("test@example.com");
        createDto.setPassword("pass");
        UserReadDto readDto = new UserReadDto();
        readDto.setEmail("test@example.com");
        when(authService.register(any(UserCreateDto.class))).thenReturn(readDto);

        ResponseEntity<SuccessResponseEntity<UserReadDto>> response = authController.register(createDto);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("test@example.com", response.getBody().getData().getEmail());
    }

    @Test
    void login_success() {
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setEmail("test@example.com");
        loginDto.setPassword("pass");
        UserInfoDto infoDto = new UserInfoDto();
        infoDto.setEmail("test@example.com");
        when(authService.login(any(UserLoginDto.class))).thenReturn(infoDto);

        ResponseEntity<SuccessResponseEntity<UserInfoDto>> response = authController.login(loginDto);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("test@example.com", response.getBody().getData().getEmail());
    }

    @Test
    void googleLogin_success_existingUser() {
        TokenDto tokenDto = new TokenDto();
        tokenDto.setIdToken("valid_token");
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn("test@example.com");
        when(payload.get("name")).thenReturn("Test User");
        when(googleVerifierService.verify("valid_token")).thenReturn(payload);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setName("Test User");
        user.setRole(UserRole.USER);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(user)).thenReturn("access_token");
        RefreshTokenReadDto refreshTokenDto = new RefreshTokenReadDto();
        when(refreshTokenService.createRefreshToken(user.getId())).thenReturn(refreshTokenDto);
        UserReadDto userReadDto = new UserReadDto();
        userReadDto.setUserPreference(null);
        when(userMapper.toUserReadDto(user)).thenReturn(userReadDto);

        ResponseEntity<SuccessResponseEntity<UserInfoDto>> response = authController.googleLogin(tokenDto);
        assertEquals(200, response.getStatusCodeValue());
        UserInfoDto result = response.getBody().getData();
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test User", result.getName());
        assertEquals("access_token", result.getToken());
    }

    @Test
    void googleLogin_invalidToken() {
        TokenDto tokenDto = new TokenDto();
        tokenDto.setIdToken("invalid_token");
        when(googleVerifierService.verify("invalid_token")).thenReturn(null);
        Exception ex = assertThrows(RuntimeException.class, () -> authController.googleLogin(tokenDto));
        assertTrue(ex.getMessage().contains("Invalid Google ID token"));
    }

    @Test
    void refresh_success() {
        RefreshTokenRequestDto requestDto = new RefreshTokenRequestDto();
        requestDto.setRefreshToken("valid_refresh_token");

        UserInfoDto userInfoDto = new UserInfoDto();
        userInfoDto.setId(UUID.randomUUID().toString());
        userInfoDto.setEmail("test@example.com");
        userInfoDto.setName("Test User");
        userInfoDto.setToken("new_access_token");
        RefreshTokenReadDto refreshTokenReadDto = new RefreshTokenReadDto();
        userInfoDto.setRefreshToken(refreshTokenReadDto);

        when(authService.refreshToken("valid_refresh_token")).thenReturn(userInfoDto);

        ResponseEntity<SuccessResponseEntity<UserInfoDto>> response = authController.refresh(requestDto);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        UserInfoDto result = response.getBody().getData();
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test User", result.getName());
        assertEquals("new_access_token", result.getToken());
        assertNotNull(result.getRefreshToken());
        verify(authService).refreshToken("valid_refresh_token");
    }

    @Test
    void refresh_invalidToken() {
        RefreshTokenRequestDto requestDto = new RefreshTokenRequestDto();
        requestDto.setRefreshToken("invalid_refresh_token");

        when(authService.refreshToken("invalid_refresh_token"))
                .thenThrow(new BadRequestException("Refresh token is not valid"));

        BadRequestException exception = assertThrows(BadRequestException.class, 
                () -> authController.refresh(requestDto));
        assertEquals("Refresh token is not valid", exception.getMessage());
        verify(authService).refreshToken("invalid_refresh_token");
    }

    @Test
    void refresh_expiredToken() {
        RefreshTokenRequestDto requestDto = new RefreshTokenRequestDto();
        requestDto.setRefreshToken("expired_refresh_token");

        when(authService.refreshToken("expired_refresh_token"))
                .thenThrow(new BadRequestException("Refresh token is expired"));

        BadRequestException exception = assertThrows(BadRequestException.class, 
                () -> authController.refresh(requestDto));
        assertEquals("Refresh token is expired", exception.getMessage());
        verify(authService).refreshToken("expired_refresh_token");
    }

    @Test
    void refresh_revokedToken() {
        RefreshTokenRequestDto requestDto = new RefreshTokenRequestDto();
        requestDto.setRefreshToken("revoked_refresh_token");

        when(authService.refreshToken("revoked_refresh_token"))
                .thenThrow(new BadRequestException("Refresh token has been revoked"));

        BadRequestException exception = assertThrows(BadRequestException.class, 
                () -> authController.refresh(requestDto));
        assertEquals("Refresh token has been revoked", exception.getMessage());
        verify(authService).refreshToken("revoked_refresh_token");
    }
}
