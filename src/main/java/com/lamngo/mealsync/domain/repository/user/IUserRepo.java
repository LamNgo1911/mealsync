package com.lamngo.mealsync.domain.repository.user;

import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserRepo {
    User save(User user);
    List<User> findAll();
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    void deleteById(UUID id);
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);
}
