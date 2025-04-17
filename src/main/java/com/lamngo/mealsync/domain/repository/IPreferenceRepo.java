package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.user.UserPreference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IPreferenceRepo {
    void savePreference(UserPreference userPreference);
    List<UserPreference> getAllPreferences();
    void deletePreference(UUID id);
    Optional<UserPreference> getPreferenceById(UUID id);
    Optional<UserPreference> getPreferenceByUserId(UUID userId);
}
