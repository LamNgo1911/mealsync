package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface IUserRepo {
    User save(User user);
    List<User> findAll();
    Optional<User> findById(String id);
    Optional<User> findByEmail(String email);
    void deleteById(String id);
}
