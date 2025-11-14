package com.lamngo.mealsync.infrastructure.repository.user;

import com.lamngo.mealsync.domain.model.user.EmailVerificationToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IEmailVerificationTokenRepo;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EmailVerificationTokenRepo implements IEmailVerificationTokenRepo {
    
    private final EmailVerificationTokenJpaRepo jpaRepo;

    public EmailVerificationTokenRepo(EmailVerificationTokenJpaRepo jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        return jpaRepo.save(token);
    }

    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return jpaRepo.findByToken(token);
    }

    @Override
    public Optional<EmailVerificationToken> findByUser(User user) {
        return jpaRepo.findByUser(user);
    }

    @Override
    public void delete(EmailVerificationToken token) {
        jpaRepo.delete(token);
    }

    @Override
    public void deleteByUser(User user) {
        jpaRepo.deleteByUser(user);
    }
}

