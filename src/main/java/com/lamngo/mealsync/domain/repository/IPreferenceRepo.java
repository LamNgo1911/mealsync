package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.Preference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IPreferenceRepo {
    void savePreference(Preference preference);
    List<Preference> getAllPreferences();
    void deletePreference(UUID id);
    Optional<Preference> getPreferenceById(UUID id);
    Optional<Preference> getPreferenceByUserId(UUID userId);
}
