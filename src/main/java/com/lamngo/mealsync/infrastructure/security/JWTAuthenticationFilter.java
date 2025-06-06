package com.lamngo.mealsync.infrastructure.security;

import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull; // Use Spring'sNonNull
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService; // Use the interface
import org.springframework.stereotype.Component; // Add Component annotation
import org.springframework.util.StringUtils; // Use Spring's StringUtils
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Component // Make it a Spring-managed component
public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService customUserDetailsService;

    public JWTAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService customUserDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();

            // List public endpoints that do NOT require JWT validation
            List<String> publicEndpoints = List.of("/api/v1/users/login", "/api/v1/users/register");

            if (publicEndpoints.contains(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = extractToken(request);
            if(token == null) {
                logger.warn("JWT Token is missing");
                throw new BadRequestException("JWT Token is missing");
            }

            if (StringUtils.hasText(token)) { // Use Spring's StringUtils.hasText
                String email = jwtTokenProvider.extractEmail(token);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

                if (jwtTokenProvider.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("Authentication set for user: {}", email); // Log successful authentication
                }
                else{
                    logger.warn("JWT Token is invalid");
                    throw new UnauthorizedException("JWT Token is invalid");
                }
            }

        } catch (Exception e) {
            logger.error("Could not set user authentication: {}", e.getMessage());
            throw new UnauthorizedException("Could not set user authentication: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
