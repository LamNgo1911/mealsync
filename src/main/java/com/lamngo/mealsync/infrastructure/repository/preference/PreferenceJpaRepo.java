package com.lamngo.mealsync.infrastructure.repository.preference;

import com.lamngo.mealsync.domain.model.Preference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreferenceJpaRepo extends JpaRepository<Preference, UUID> {
    Optional<Preference> findByUserId(UUID userId);
    List<Preference> findAll();
    void deleteByUserId(UUID userId);
    Optional<Preference> findById(UUID id);
}
