package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class AuthService implements IAuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final IUserRepo _userRepo;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    UserMapper _userMapper;

    public AuthService( AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, IUserRepo userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this._userRepo = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserReadDto register(UserCreateDto userCreateDto) {
        // Check if the email already exists
        Optional<User> existingUser = _userRepo.findByEmail(userCreateDto.getEmail());
        if (existingUser.isPresent()) {
            throw new BadRequestException("Email already exists");
        }

        // Create a new user
        User user = new User();
        user.setEmail(userCreateDto.getEmail());
        user.setPassword(passwordEncoder.encode(userCreateDto.getPassword()));
        user.setName(userCreateDto.getName());
        user.setRole(userCreateDto.getRole() == null? UserRole.USER : userCreateDto.getRole() );

        // Create a default UserPreference
        UserPreference userPreference = new UserPreference();

        userPreference.setDietaryRestrictions(new ArrayList<>());
        userPreference.setFavoriteCuisines(new ArrayList<>());
        userPreference.setDislikedIngredients(new ArrayList<>());

        userPreference.setUser(user);
        System.out.println("User preference: " + userPreference);
        user.setUserPreference(userPreference);
        System.out.println("User: " + user);

        user = _userRepo.save(user);
        return _userMapper.toUserReadDto(user);
    }

    @Override
    public String login(UserLoginDto userLoginDto) {

        String email = userLoginDto.getEmail();
        String password = userLoginDto.getPassword();

        // Validate email and password
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
        // Generate JWT token
        return jwtTokenProvider.generateToken(userDetails);
    }

    @Override
    public void logout(String token) {
        // Implement token revocation (e.g., store invalid tokens in a blacklist)
        System.out.println("Token revoked: " + token);
    }

    @Override
    public void changePassword(String email, String oldPassword, String newPassword) {
        Optional<User> userOptional = _userRepo.findByEmail(email);

        if (userOptional.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }

        User user = userOptional.get();

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadRequestException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        _userRepo.save(user);
    }

    @Override
    public boolean validateToken(String token) {
        // Extract email from token
        String email = jwtTokenProvider.extractEmail(token);
        // Find user by email
        Optional<User> userOptional = _userRepo.findByEmail(email);
        if (userOptional.isEmpty()) {
            return false;
        }

        User user = userOptional.get();

        // Validate token
        return jwtTokenProvider.validateToken(token, user);
    }

    @Override
    public void refreshToken(String token) {
        // Implement token refresh logic
        // This typically involves generating a new token with a new expiration time
        System.out.println("Token refreshed: " + token);
    }
}

