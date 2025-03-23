package com.lamngo.mealsync.infrastructure.repository.preference;

import com.lamngo.mealsync.domain.model.Preference;
import com.lamngo.mealsync.domain.repository.IPreferenceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PreferenceRepo implements IPreferenceRepo {

    @Autowired
    private PreferenceJpaRepo _preferenceJpaRepo;
    @Override
    public void savePreference(Preference preference) {
        _preferenceJpaRepo.save(preference);
    }

    @Override
    public List<Preference> getAllPreferences() {
        return _preferenceJpaRepo.findAll();
    }

    @Override
    public void deletePreference(String id) {
        _preferenceJpaRepo.deleteById(id);
    }

    @Override
    public void updatePreference(Preference preference) {
        _preferenceJpaRepo.updatePreference(preference);
    }

    @Override
    public Optional<Preference> getPreferenceById(String id) {
        return _preferenceJpaRepo.findById(id);
    }

    @Override
    public Optional<Preference> getPreferenceByUserId(String userId) {
        return _preferenceJpaRepo.findByUserId(userId);
    }
}
