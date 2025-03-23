package com.lamngo.mealsync.infrastructure.repository.preference;

import com.lamngo.mealsync.domain.model.Preference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceJpaRepo extends JpaRepository<Preference, String> {
    Optional<Preference> findByUserId(String userId);
    List<Preference> findAll();
    void deleteByUserId(String userId);
    void updatePreference(Preference preference);
    Optional<Preference> findById(String id);
}
