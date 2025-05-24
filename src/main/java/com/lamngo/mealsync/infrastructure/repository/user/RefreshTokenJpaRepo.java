package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenJpaRepo extends JpaRepository<RefreshToken, UUID> {
    RefreshToken findByToken(String token);

    void deleteByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);

    RefreshToken findByUserId(UUID userId);
}
