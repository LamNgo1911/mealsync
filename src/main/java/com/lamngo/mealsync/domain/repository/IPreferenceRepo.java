package com.lamngo.mealsync.domain.repository;

import com.lamngo.mealsync.domain.model.Preference;

import java.util.List;

public interface IPreferenceRepo {
    void savePreference(Preference preference);
    List<Preference> getAllPreferences();
    void deletePreference(String id);
    void updatePreference(Preference preference);
    Preference getPreferenceById(String id);
    Preference getPreferenceByUserId(String userId);
}
