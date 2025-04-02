package com.lamngo.mealsync.application.service.auth;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserLoginDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.domain.model.Role;
import com.lamngo.mealsync.domain.model.User;
import com.lamngo.mealsync.domain.repository.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

@Service
public class AuthService implements IAuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final IUserRepo _userRepo;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    UserMapper _userMapper;

    public AuthService( AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, IUserRepo userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this._userRepo = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserReadDto register(UserCreateDto userCreateDto) {
        User user = new User();
        user.setEmail(userCreateDto.getEmail());
        user.setPassword(passwordEncoder.encode(userCreateDto.getPassword()));
        user.setName(userCreateDto.getName());
        user.setRole(userCreateDto.getRole() == null? Role.USER : userCreateDto.getRole() );
        user = _userRepo.save(user);
        return _userMapper.toUserReadDto(user);
    }

    @Override
    public String login(UserLoginDto userLoginDto) {

        String email = userLoginDto.getEmail();
        String password = userLoginDto.getPassword();

        // Validate email and password
        if (email == null || password == null) {
            throw new RuntimeException("Email and password must not be null");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Invalid email or password");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return jwtTokenProvider.generateToken(userDetails);
    }
}
