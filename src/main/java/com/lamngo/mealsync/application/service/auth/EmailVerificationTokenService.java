package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.domain.model.user.EmailVerificationToken;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.repository.user.IEmailVerificationTokenRepo;
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
public class EmailVerificationTokenService {
    
    private static final int TOKEN_EXPIRY_HOURS = 24;
    private static final int TOKEN_LENGTH = 32;
    private final IEmailVerificationTokenRepo tokenRepo;
    private final EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationTokenService(IEmailVerificationTokenRepo tokenRepo, EntityManager entityManager) {
        this.tokenRepo = tokenRepo;
        this.entityManager = entityManager;
    }

    @Transactional
    public EmailVerificationToken createToken(User user) {
        // Delete any existing token for this user and flush to ensure constraint is satisfied
        tokenRepo.deleteByUser(user);
        entityManager.flush();
        
        String token = generateSecureToken();
        Instant expiryDate = Instant.now().plusSeconds(TOKEN_EXPIRY_HOURS * 3600L);
        
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(user);
        verificationToken.setExpiryDate(expiryDate);
        verificationToken.setUsed(false);
        
        return tokenRepo.save(verificationToken);
    }

    @Transactional
    public User verifyToken(String token) {
        EmailVerificationToken verificationToken = tokenRepo.findByToken(token)
            .orElseThrow(() -> new BadRequestException("Invalid verification token"));
        
        if (verificationToken.isUsed()) {
            throw new BadRequestException("Verification token has already been used");
        }
        
        if (Instant.now().isAfter(verificationToken.getExpiryDate())) {
            throw new BadRequestException("Verification token has expired");
        }
        
        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        
        verificationToken.setUsed(true);
        tokenRepo.save(verificationToken);
        
        log.info("Email verified for user: {}", user.getEmail());
        return user;
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

