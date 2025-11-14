package com.lamngo.mealsync.domain.repository.user;

import com.lamngo.mealsync.domain.model.user.PasswordResetToken;
import com.lamngo.mealsync.domain.model.user.User;

import java.util.Optional;

public interface IPasswordResetTokenRepo {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByUser(User user);
    void delete(PasswordResetToken token);
    void deleteByUser(User user);
}

