package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.service.user.UserService;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Only users with the ADMIN role can access this endpoint
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponseEntity<List<UserReadDto>>> getAllUsers() {
        List<UserReadDto> users = userService.getAllUsers();
        SuccessResponseEntity<List<UserReadDto>> body = new SuccessResponseEntity<>();
        body.setData(users);
        return ResponseEntity.ok(body);
    }

    // Any authenticated user can access their own user data
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') and #id == #userDetails.id")
    public ResponseEntity<SuccessResponseEntity<UserReadDto>> findUserById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserReadDto user = userService.findUserById(id);
        SuccessResponseEntity<UserReadDto> body = new SuccessResponseEntity<>();
        body.setData(user);
        return ResponseEntity.ok(body);
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
    public ResponseEntity<SuccessResponseEntity<UserReadDto>> updateUser(@PathVariable UUID id, @RequestBody @Valid UserUpdateDto userUpdateDto) {
        UserReadDto updatedUser = userService.updateUser(id, userUpdateDto);
        SuccessResponseEntity<UserReadDto> body = new SuccessResponseEntity<>();
        body.setData(updatedUser);
        return ResponseEntity.ok(body);
    }

    // update user preference
    @PutMapping("/{id}/preference")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<SuccessResponseEntity<UserReadDto>> updateUserPreferenceById(@PathVariable UUID id, @RequestBody @Valid UserPreferenceUpdateDto userPreferenceUpdateDto) {
        UserPreferenceReadDto updatedUserPreference = userService.updateUserPreferencesById(id, userPreferenceUpdateDto);
        UserReadDto updatedUser = userService.findUserById(id);
        updatedUser.setUserPreference(updatedUserPreference);

        // Return the updated user with the updated preference
        SuccessResponseEntity<UserReadDto> body = new SuccessResponseEntity<>();
        body.setData(updatedUser);
        return ResponseEntity.ok(body);
    }
}
