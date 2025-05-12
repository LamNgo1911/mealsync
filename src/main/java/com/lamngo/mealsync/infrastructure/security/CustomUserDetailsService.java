package com.lamngo.mealsync.infrastructure.security;

import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final IUserRepo _userRepo;

    // Constructor injection is preferred
    public CustomUserDetailsService(IUserRepo _userRepo) {
        this._userRepo = _userRepo;
    }

    @Override
    @Transactional(readOnly = true) // Add @Transactional for proper session handling
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Loading user by email: {}", email);
        return _userRepo.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User not found with email: {}", email); // Log at WARN level
                    return new UsernameNotFoundException("User not found with email: " + email);
                });
    }
}
