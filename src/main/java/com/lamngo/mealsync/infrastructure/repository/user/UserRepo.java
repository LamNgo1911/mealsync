package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.IUserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
