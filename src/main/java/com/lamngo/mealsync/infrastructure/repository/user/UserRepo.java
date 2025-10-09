package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.model.user.UserStatus;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UserRepo implements IUserRepo {
    @Autowired
    private UserJpaRepo _userJpaRepo;

    @Override
    public User save(User user) {
        return _userJpaRepo.save(user);
    }

    @Override
    public List<User> findAll() {
        return _userJpaRepo.findAll();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return _userJpaRepo.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return _userJpaRepo.findByEmail(email);
    }

    @Override
    public void deleteById(UUID id) {
        _userJpaRepo.deleteById(id);
    }

    @Override
    public List<User> findByRoleAndStatus(UserRole role, UserStatus status) {
        return _userJpaRepo.findAll().stream()
                .filter(user -> (role == null || user.getRole() == role) &&
                               (status == null || user.getStatus() == status))
                .collect(Collectors.toList());
    }
}
