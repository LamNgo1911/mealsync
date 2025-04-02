package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepo extends JpaRepository<User, UUID> {
    User save(User user);
    List<User> findAll();
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    void deleteById(UUID id);
}
