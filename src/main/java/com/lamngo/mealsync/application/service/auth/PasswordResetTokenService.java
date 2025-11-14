package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.domain.model.user.PasswordResetToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IPasswordResetTokenRepo;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@Slf4j
public class PasswordResetTokenService {
    
    private static final int TOKEN_EXPIRY_HOURS = 1; // 1 hour expiry for password reset
    private static final int TOKEN_LENGTH = 32;
    private final IPasswordResetTokenRepo tokenRepo;
    private final EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetTokenService(IPasswordResetTokenRepo tokenRepo, EntityManager entityManager) {
        this.tokenRepo = tokenRepo;
        this.entityManager = entityManager;
    }

    @Transactional
    public PasswordResetToken createToken(User user) {
        // Delete any existing token for this user and flush to ensure constraint is satisfied
        tokenRepo.deleteByUser(user);
        entityManager.flush();
        
        String token = generateSecureToken();
        Instant expiryDate = Instant.now().plusSeconds(TOKEN_EXPIRY_HOURS * 3600L);
        
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(expiryDate);
        resetToken.setUsed(false);
        
        return tokenRepo.save(resetToken);
    }

    @Transactional
    public User validateToken(String token) {
        PasswordResetToken resetToken = tokenRepo.findByToken(token)
            .orElseThrow(() -> new BadRequestException("Invalid password reset token"));
        
        if (resetToken.isUsed()) {
            throw new BadRequestException("Password reset token has already been used");
        }
        
        if (Instant.now().isAfter(resetToken.getExpiryDate())) {
            throw new BadRequestException("Password reset token has expired");
        }
        
        return resetToken.getUser();
    }

    @Transactional
    public void markTokenAsUsed(String token) {
        PasswordResetToken resetToken = tokenRepo.findByToken(token)
            .orElseThrow(() -> new BadRequestException("Invalid password reset token"));
        
        resetToken.setUsed(true);
        tokenRepo.save(resetToken);
        log.info("Password reset token marked as used for user: {}", resetToken.getUser().getEmail());
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

