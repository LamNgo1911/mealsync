package com.lamngo.mealsync.infrastructure.repository;

import com.lamngo.mealsync.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserJpaRepo extends JpaRepository<User, String> {
    User save(User user);
    List<User> findAll();
    Optional<User> findById(String id);
    Optional<User> findByEmail(String email);
    void deleteById(String id);
}
