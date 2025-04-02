package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserUpdateDto;
import com.lamngo.mealsync.application.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Import UserDetails
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Only users with the ADMIN role can access this endpoint
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserReadDto>> getAllUsers() {
        List<UserReadDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    // Any authenticated user can access their own user data
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') and #id == #userDetails.id")
    public ResponseEntity<UserReadDto> findUserById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) { // Inject UserDetails
        UserReadDto user = userService.findUserById(id);
        return ResponseEntity.ok(user);
    }

    // Only users with the ADMIN role can delete users
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID id) {
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Only users with the ADMIN role can update user data
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserReadDto> updateUser(@PathVariable UUID id, @RequestBody @Valid UserUpdateDto userUpdateDto) {
        UserReadDto updatedUser = userService.updateUser(id, userUpdateDto);
        return ResponseEntity.ok(updatedUser);
    }
}
