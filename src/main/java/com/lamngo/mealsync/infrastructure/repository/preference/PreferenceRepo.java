package com.lamngo.mealsync.infrastructure.repository.preference;

import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.user.IPreferenceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PreferenceRepo implements IPreferenceRepo {

    @Autowired
    private PreferenceJpaRepo _preferenceJpaRepo;

    @Override
    public void savePreference(UserPreference userPreference) {
        _preferenceJpaRepo.save(userPreference);
    }

    @Override
    public List<UserPreference> getAllPreferences() {
        return _preferenceJpaRepo.findAll();
    }

    @Override
    public void deletePreference(UUID id) {
        _preferenceJpaRepo.deleteById(id);
    }


    @Override
    public Optional<UserPreference> getPreferenceById(UUID id) {
        return _preferenceJpaRepo.findById(id);
    }

    @Override
    public Optional<UserPreference> getPreferenceByUserId(UUID userId) {
        return _preferenceJpaRepo.findByUserId(userId);
    }
}
