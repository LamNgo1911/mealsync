package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.service.auth.AuthService;
import com.lamngo.mealsync.application.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Anyone can register a new user
    @PostMapping("/register")
    public ResponseEntity<UserReadDto> register(@RequestBody @Valid UserCreateDto userCreateDto) {
        UserReadDto createdUser = authService.register(userCreateDto);
        return ResponseEntity.ok(createdUser);
    }

    // Anyone can log in
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody @Valid UserLoginDto userLoginDto) {

        String token = authService.login(userLoginDto);
        return ResponseEntity.ok(token);
    }
}
