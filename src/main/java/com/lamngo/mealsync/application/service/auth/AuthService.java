package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.domain.model.user.RefreshToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.repository.user.IRefreshTokenRepo;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final IUserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final IRefreshTokenRepo refreshTokenRepo;

    @Autowired
    UserMapper userMapper;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            IUserRepo userRepo,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
            , IRefreshTokenRepo refreshTokenRepo
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepo = refreshTokenRepo;
    }

    @Override
    @Transactional
    public UserReadDto register(UserCreateDto userCreateDto) {
        Optional<User> existingUser = userRepo.findByEmail(userCreateDto.getEmail());
        if (existingUser.isPresent()) {
            throw new BadRequestException("Email already exists");
        }

        User user = new User();
        user.setEmail(userCreateDto.getEmail());
        user.setPassword(passwordEncoder.encode(userCreateDto.getPassword()));
        user.setName(userCreateDto.getName());
        user.setRole(userCreateDto.getRole() == null ? UserRole.USER : userCreateDto.getRole());

        UserPreference preference = new UserPreference();
        preference.setDietaryRestrictions(new ArrayList<>());
        preference.setFavoriteCuisines(new ArrayList<>());
        preference.setDislikedIngredients(new ArrayList<>());
        preference.setUser(user);

        user.setUserPreference(preference);

        return userMapper.toUserReadDto(userRepo.save(user));
    }

    @Override
    @Transactional
    public UserInfoDto login(UserLoginDto userLoginDto) {
        String email = userLoginDto.getEmail();
        String password = userLoginDto.getPassword();

        if (email == null || password == null) {
            throw new BadRequestException("Email and password must not be null");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("Invalid email or password");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = (User) userDetails;

        String token = jwtTokenProvider.generateToken(userDetails);
        RefreshTokenReadDto refreshToken = refreshTokenService.createRefreshToken(user.getId());

        UserReadDto userReadDto = userMapper.toUserReadDto(user);

        return UserInfoDto.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .userPreference(userReadDto.getUserPreference())
                .token(token)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public void logout(String token) {
        String email = jwtTokenProvider.extractEmail(token);
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Invalidate all user's refresh tokens (can be made more selective)
        refreshTokenService.deleteByUserId(user.getId());
    }

    @Override
    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadRequestException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Override
    public boolean validateToken(String token) {
        String email = jwtTokenProvider.extractEmail(token);
        return userRepo.findByEmail(email)
                .map(user -> jwtTokenProvider.validateToken(token, user))
                .orElse(false);
    }

    @Override
    public UserInfoDto refreshToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepo.findByToken(refreshTokenStr);

        if (refreshToken == null) {
            throw new BadRequestException("Refresh token is not valid");
        }

        // Verify the expiration of the refresh token
        if (!refreshTokenService.verifyExpiration(refreshToken)) {
            throw new BadRequestException("Refresh token is expired");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtTokenProvider.generateToken(user);
        RefreshTokenReadDto newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        UserReadDto userReadDto = userMapper.toUserReadDto(user);

        return UserInfoDto.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .userPreference(userReadDto.getUserPreference())
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
