package com.lamngo.mealsync.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.application.service.auth.AuthService;
import com.lamngo.mealsync.application.service.auth.GoogleVerifierService;
import com.lamngo.mealsync.application.service.auth.RefreshTokenService;
import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;
    @MockBean
    private GoogleVerifierService googleVerifierService;
    @MockBean
    private IUserRepo userRepo;
    @MockBean
    private RefreshTokenService refreshTokenService;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/v1/users/register - success")
    void register_success() throws Exception {
        UserCreateDto createDto = new UserCreateDto();
        createDto.setEmail("test@example.com");
        createDto.setPassword("pass");
        UserReadDto readDto = new UserReadDto();
        readDto.setEmail("test@example.com");
        when(authService.register(any(UserCreateDto.class))).thenReturn(readDto);
        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/users/login - success")
    void login_success() throws Exception {
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setEmail("test@example.com");
        loginDto.setPassword("pass");
        UserInfoDto infoDto = new UserInfoDto();
        infoDto.setEmail("test@example.com");
        when(authService.login(any(UserLoginDto.class))).thenReturn(infoDto);
        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/users/login/google - success")
    void googleLogin_success() throws Exception {
        TokenDto tokenDto = new TokenDto();
        tokenDto.setIdToken("valid_token");
        // Mock GoogleIdToken.Payload
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(payload.getEmail()).thenReturn("test@example.com");
        when(payload.get("name")).thenReturn("Test User");
        when(googleVerifierService.verify("valid_token")).thenReturn(payload);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setName("Test User");
        user.setRole(UserRole.USER);
        when(userRepo.findByEmail("test@example.com")).thenReturn(java.util.Optional.of(user));
        when(jwtTokenProvider.generateToken(user)).thenReturn("access_token");
        RefreshToken refreshToken = new RefreshToken();
        when(refreshTokenService.createRefreshToken(user.getId())).thenReturn(refreshToken);
        UserReadDto userReadDto = new UserReadDto();
        userReadDto.setUserPreference(null);
        when(userMapper.toUserReadDto(user)).thenReturn(userReadDto);
        mockMvc.perform(post("/api/v1/users/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("Test User"))
                .andExpect(jsonPath("$.data.token").value("access_token"));
    }

    @Test
    @DisplayName("POST /api/v1/users/login/google - invalid token")
    void googleLogin_invalidToken() throws Exception {
        TokenDto tokenDto = new TokenDto();
        tokenDto.setIdToken("invalid_token");
        when(googleVerifierService.verify("invalid_token")).thenReturn(null);
        mockMvc.perform(post("/api/v1/users/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenDto)))
                .andExpect(status().isBadRequest());
    }
}
