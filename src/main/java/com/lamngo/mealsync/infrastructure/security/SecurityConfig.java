//package com.lamngo.mealsync.presentation.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//
//    private final JwtAuthenticationFilter jwtAuthenticationFilter;
//    private final UserService userService; // Your UserService for loading users
//
//    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, UserService userService) {
//        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
//        this.userService = userService;
//    }
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf().disable() // Disable CSRF since this is a stateless API
//                .sessionManagement()
//                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // No sessions for JWT
//                .and()
//                .authorizeHttpRequests((requests) -> requests
//                        .requestMatchers("/api/v1/users/register", "/api/v1/users/login").permitAll() // Public endpoints
//                        .requestMatchers("/api/v1/users/**").authenticated() // Protected endpoints
//                        .anyRequest().authenticated() // Any other request requires authentication
//                )
//                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
//
//    @Bean
//    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
//        return authenticationConfiguration.getAuthenticationManager();
//    }
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//}