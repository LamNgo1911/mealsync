package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.user.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserRepo {
    User save(User user);
    List<User> findAll();
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    void deleteById(UUID id);
}
