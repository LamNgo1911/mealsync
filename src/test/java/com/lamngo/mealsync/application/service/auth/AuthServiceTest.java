package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.mapper.user.RefreshTokenMapper;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.domain.model.user.*;
import com.lamngo.mealsync.domain.repository.user.IRefreshTokenRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private IUserRepo userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private IRefreshTokenRepo refreshTokenRepo;
    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(authenticationManager, jwtTokenProvider, userRepo, passwordEncoder, refreshTokenService, refreshTokenRepo);
        authService.userMapper = userMapper;

    }

    @Test
    void register_shouldThrowException_whenEmailExists() {
        UserCreateDto dto = new UserCreateDto();
        dto.setEmail("test@example.com");
        when(userRepo.findByEmail(dto.getEmail())).thenReturn(Optional.of(new User()));
        assertThrows(BadRequestException.class, () -> authService.register(dto));
    }

    @Test
    void register_shouldSucceed_whenEmailNotExists() {
        UserCreateDto dto = new UserCreateDto();
        dto.setEmail("test2@example.com");
        dto.setPassword("pass");
        dto.setName("Test");

        when(userRepo.findByEmail(dto.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toUserReadDto(any(User.class))).thenReturn(new UserReadDto());
        assertNotNull(authService.register(dto));
    }

    @Test
    void login_shouldThrowException_whenEmailOrPasswordNull() {
        UserLoginDto dto = new UserLoginDto();
        dto.setEmail(null);
        dto.setPassword(null);
        assertThrows(BadRequestException.class, () -> authService.login(dto));
    }

    @Test
    void login_shouldThrowException_whenAuthenticationFails() {
        UserLoginDto dto = new UserLoginDto();
        dto.setEmail("test@example.com");
        dto.setPassword("pass");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        assertThrows(BadRequestException.class, () -> authService.login(dto));
    }

    // Removed: login_shouldThrowException_whenUserNotFound
    // This test scenario is impossible because if authentication succeeds,
    // the User object is already in the authentication principal.
    // The userRepo.findByEmail() is not actually called in the login flow.

    @Test
    void login_shouldSucceed_whenValid() {
        UserLoginDto dto = new UserLoginDto();
        dto.setEmail("test@example.com");
        dto.setPassword("pass");
        Authentication auth = mock(Authentication.class);
        // Use actual User object (which implements UserDetails) instead of mocking UserDetails
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setId(UUID.randomUUID());
        user.setName("Test");
        user.setRole(UserRole.USER);
        user.setPassword("encodedPassword");
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(user); // Return User, not UserDetails mock
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("token");
        when(refreshTokenService.createRefreshToken(user.getId())).thenReturn(new RefreshTokenReadDto());
        UserReadDto userReadDto = new UserReadDto();
        userReadDto.setUserPreference(new UserPreferenceReadDto());
        when(userMapper.toUserReadDto(any(User.class))).thenReturn(userReadDto);
        UserInfoDto result = authService.login(dto);
        assertNotNull(result);
        assertEquals(dto.getEmail(), result.getEmail());
    }

    @Test
    void logout_shouldThrowException_whenUserNotFound() {
        when(jwtTokenProvider.extractEmail(any())).thenReturn("test@example.com");
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.logout("token"));
    }

    @Test
    void logout_shouldCallDeleteByUserId_whenUserFound() {
        User user = new User(); user.setId(UUID.randomUUID()); user.setEmail("test@example.com");
        when(jwtTokenProvider.extractEmail(any())).thenReturn(user.getEmail());
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        doNothing().when(refreshTokenService).deleteByUserId(user.getId());
        assertDoesNotThrow(() -> authService.logout("token"));
        verify(refreshTokenService, times(1)).deleteByUserId(user.getId());
    }

    @Test
    void changePassword_shouldThrowException_whenUserNotFound() {
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.changePassword("email", "old", "new"));
    }

    @Test
    void changePassword_shouldThrowException_whenOldPasswordIncorrect() {
        User user = new User(); user.setPassword("encoded");
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        assertThrows(BadRequestException.class, () -> authService.changePassword("email", "old", "new"));
    }

    @Test
    void changePassword_shouldSucceed_whenOldPasswordCorrect() {
        User user = new User(); user.setPassword("encoded");
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("newEncoded");
        when(userRepo.save(any(User.class))).thenReturn(user);
        assertDoesNotThrow(() -> authService.changePassword("email", "old", "new"));
    }

    @Test
    void validateToken_shouldReturnFalse_whenUserNotFound() {
        when(jwtTokenProvider.extractEmail(any())).thenReturn("email");
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        assertFalse(authService.validateToken("token"));
    }

    @Test
    void validateToken_shouldReturnTrue_whenValid() {
        User user = new User();
        when(jwtTokenProvider.extractEmail(any())).thenReturn("email");
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(jwtTokenProvider.validateToken(any(), eq(user))).thenReturn(true);
        assertTrue(authService.validateToken("token"));
    }

    @Test
    void refreshToken_shouldReturnUserInfoDto_whenValid() {
        RefreshToken refreshToken = new RefreshToken();
        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setName("Test");
        user.setRole(UserRole.USER);
        refreshToken.setUser(user);
        when(refreshTokenRepo.findByToken(any())).thenReturn(refreshToken);
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(true);
        String expectedToken = "token";
        RefreshTokenReadDto newRefreshToken = new RefreshTokenReadDto(); // Mock the refresh token read dto here
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn(expectedToken);
        when(refreshTokenService.createRefreshToken(user.getId())).thenReturn(new RefreshTokenReadDto());
        UserReadDto userReadDto = new UserReadDto();
        UserPreferenceReadDto userPreferenceReadDto = new UserPreferenceReadDto();
        userReadDto.setUserPreference(userPreferenceReadDto);
        when(userMapper.toUserReadDto(user)).thenReturn(userReadDto);
        UserInfoDto result = authService.refreshToken("refreshToken");
        assertNotNull(result);
        assertEquals(user.getId().toString(), result.getId());
        assertEquals(user.getEmail(), result.getEmail());
        assertEquals(user.getName(), result.getName());
        assertEquals(user.getRole(), result.getRole());
        assertEquals(userPreferenceReadDto, result.getUserPreference());
        assertEquals(expectedToken, result.getToken());
        assertEquals(newRefreshToken, result.getRefreshToken());
    }

    @Test
    void refreshToken_shouldThrowException_whenTokenIsInvalid() {
        when(refreshTokenRepo.findByToken(any())).thenReturn(null);
        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.refreshToken("invalidToken"));
        assertEquals("Refresh token is not valid", exception.getMessage());
    }

    @Test
    void refreshToken_shouldThrowException_whenTokenIsExpired() {
        RefreshToken refreshToken = new RefreshToken();
        when(refreshTokenRepo.findByToken(any())).thenReturn(refreshToken);
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(false);
        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.refreshToken("expiredToken"));
        assertEquals("Refresh token is expired", exception.getMessage());
    }

    @Test
    void refreshToken_shouldThrowException_whenTokenIsRevoked() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRevoked(true);
        when(refreshTokenRepo.findByToken(any())).thenReturn(refreshToken);
        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.refreshToken("revokedToken"));
        assertEquals("Refresh token has been revoked", exception.getMessage());
        // Should not call verifyExpiration if token is revoked
        verify(refreshTokenService, never()).verifyExpiration(any());
    }
}
