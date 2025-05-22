package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepo extends JpaRepository<RefreshToken, UUID> {
    RefreshToken findByToken(String token);

    void deleteByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);

    Optional<RefreshToken> findByUserId(UUID userId);
}
