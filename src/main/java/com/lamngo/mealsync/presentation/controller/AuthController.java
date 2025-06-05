package com.lamngo.mealsync.presentation.controller;

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
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class AuthController {

    private final AuthService authService;
    private final GoogleVerifierService googleVerifierService;
    private final IUserRepo userRepo;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, GoogleVerifierService googleVerifierService, IUserRepo userRepo,
                          RefreshTokenService refreshTokenService, UserMapper userMapper, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.googleVerifierService = googleVerifierService;
        this.userRepo = userRepo;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // Anyone can register a new user
    @PostMapping("/register")
    public ResponseEntity<SuccessResponseEntity<UserReadDto>> register(@RequestBody @Valid UserCreateDto userCreateDto) {
        UserReadDto createdUser = authService.register(userCreateDto);
        SuccessResponseEntity<UserReadDto> body = new SuccessResponseEntity<>();
        body.setData(createdUser);
        return ResponseEntity.ok(body);
    }

    // Anyone can log in
    @PostMapping("/login")
    public ResponseEntity<SuccessResponseEntity<UserInfoDto>> login(@RequestBody @Valid UserLoginDto userLoginDto) {

        UserInfoDto userInfo = authService.login(userLoginDto);
        SuccessResponseEntity<UserInfoDto> body = new SuccessResponseEntity<>();
        body.setData(userInfo);
        return ResponseEntity.ok(body);
    }

    // Anyone can log in with Google
    @PostMapping("/login/google")
    public ResponseEntity<SuccessResponseEntity<UserInfoDto>> googleLogin(@RequestBody TokenDto tokenDto) {
        String idTokenString = tokenDto.getIdToken();

        GoogleIdToken.Payload payload = googleVerifierService.verify(idTokenString);
        if (payload == null) {
            throw new BadRequestException("Invalid Google ID token");
        }

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        User user = userRepo.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setRole(UserRole.USER);
            return userRepo.save(newUser);
        });

        String accessToken = jwtTokenProvider.generateToken(user);
        RefreshTokenReadDto newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        UserReadDto userReadDto = userMapper.toUserReadDto(user);

        UserInfoDto userInfoDto = new UserInfoDto();
        userInfoDto.setId(user.getId().toString());
        userInfoDto.setEmail(user.getEmail());
        userInfoDto.setName(user.getName());
        userInfoDto.setRole(user.getRole());
        userInfoDto.setUserPreference(userReadDto.getUserPreference());
        userInfoDto.setToken(accessToken);
        userInfoDto.setRefreshToken(newRefreshToken);
        SuccessResponseEntity<UserInfoDto> body = new SuccessResponseEntity<>();
        body.setData(userInfoDto);
        return ResponseEntity.ok(body);
    }
}
