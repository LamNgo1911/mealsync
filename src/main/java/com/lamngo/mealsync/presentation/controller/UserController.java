package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService _userService;

    @PostMapping("/register")
    public ResponseEntity<UserReadDto> register(@RequestBody @Valid UserCreateDto userCreateDto) {
        UserReadDto userReadDto = _userService.register(userCreateDto);

        return ResponseEntity.ok(userReadDto);
    }

    @PostMapping("/login")
    public ResponseEntity<UserReadDto> login(@RequestBody @Valid String email, String password) {
        UserReadDto userReadDto = _userService.login(email, password);

        return ResponseEntity.ok(userReadDto);
    }

    @GetMapping
    public ResponseEntity<List<UserReadDto>> getAllUsers() {
        System.out.println("Get all users");
        List<UserReadDto> userReadDtos = _userService.getAllUsers();

        return ResponseEntity.ok(userReadDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserReadDto> findUserById(@PathVariable String id) {
        UserReadDto userReadDto = _userService.findUserById(id);

        return ResponseEntity.ok(userReadDto);
    }
}
