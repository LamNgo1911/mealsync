package com.lamngo.mealsync.domain.repository.user;

import com.lamngo.mealsync.domain.model.user.EmailVerificationToken;
import com.lamngo.mealsync.domain.model.user.User;

import java.util.Optional;

public interface IEmailVerificationTokenRepo {
    EmailVerificationToken save(EmailVerificationToken token);
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUser(User user);
    void delete(EmailVerificationToken token);
    void deleteByUser(User user);
}

