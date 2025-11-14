package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.PasswordResetToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IPasswordResetTokenRepo;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PasswordResetTokenRepo implements IPasswordResetTokenRepo {
    
    private final PasswordResetTokenJpaRepo jpaRepo;

    public PasswordResetTokenRepo(PasswordResetTokenJpaRepo jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return jpaRepo.save(token);
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaRepo.findByToken(token);
    }

    @Override
    public Optional<PasswordResetToken> findByUser(User user) {
        return jpaRepo.findByUser(user);
    }

    @Override
    public void delete(PasswordResetToken token) {
        jpaRepo.delete(token);
    }

    @Override
    public void deleteByUser(User user) {
        jpaRepo.deleteByUser(user);
    }
}

