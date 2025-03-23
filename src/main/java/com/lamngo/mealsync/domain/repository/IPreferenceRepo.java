package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.Preference;

import java.util.List;
import java.util.Optional;

public interface IPreferenceRepo {
    void savePreference(Preference preference);
    List<Preference> getAllPreferences();
    void deletePreference(String id);
    void updatePreference(Preference preference);
    Optional<Preference> getPreferenceById(String id);
    Optional<Preference> getPreferenceByUserId(String userId);
}
