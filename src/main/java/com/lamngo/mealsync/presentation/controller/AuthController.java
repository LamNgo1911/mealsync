package com.lamngo.mealsync.presentation.controller;

import com.lamngo.mealsync.application.dto.user.RefreshTokenRequestDto;
import com.lamngo.mealsync.application.dto.user.*;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.application.service.auth.AuthService;
import com.lamngo.mealsync.application.service.auth.EmailVerificationTokenService;
import com.lamngo.mealsync.application.service.auth.GoogleVerifierService;
import com.lamngo.mealsync.application.service.auth.RefreshTokenService;
import com.lamngo.mealsync.application.service.email.EmailService;
import com.lamngo.mealsync.application.service.email.EmailTemplateService;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserRole;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.infrastructure.security.JwtTokenProvider;
import com.lamngo.mealsync.presentation.error.BadRequestException;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import com.lamngo.mealsync.presentation.shared.SuccessResponseEntity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class AuthController {

    private final AuthService authService;
    private final GoogleVerifierService googleVerifierService;
    private final IUserRepo userRepo;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    
    @Value("${app.frontend-url:${app.base-url}}")
    private String frontendUrl;
    
    @Value("${app.mobile-deep-link-scheme:cookify://}")
    private String mobileDeepLinkScheme;

    public AuthController(AuthService authService, GoogleVerifierService googleVerifierService, IUserRepo userRepo,
                          RefreshTokenService refreshTokenService, UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
                          EmailVerificationTokenService emailVerificationTokenService, EmailService emailService,
                          EmailTemplateService emailTemplateService) {
        this.authService = authService;
        this.googleVerifierService = googleVerifierService;
        this.userRepo = userRepo;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.emailService = emailService;
        this.emailTemplateService = emailTemplateService;
    }

    // Anyone can register a new user
    @PostMapping("/register")
    public ResponseEntity<SuccessResponseEntity<UserReadDto>> register(@RequestBody @Valid UserCreateDto userCreateDto) {
        UserReadDto createdUser = authService.register(userCreateDto);
        SuccessResponseEntity<UserReadDto> body = new SuccessResponseEntity<>();
        body.setData(createdUser);
        return ResponseEntity.ok(body);
    }

    // Anyone can log in
    @PostMapping("/login")
    public ResponseEntity<SuccessResponseEntity<UserInfoDto>> login(@RequestBody @Valid UserLoginDto userLoginDto) {

        UserInfoDto userInfo = authService.login(userLoginDto);
        SuccessResponseEntity<UserInfoDto> body = new SuccessResponseEntity<>();
        body.setData(userInfo);
        return ResponseEntity.ok(body);
    }

    // Anyone can log in with Google
    @PostMapping("/login/google")
    public ResponseEntity<SuccessResponseEntity<UserInfoDto>> googleLogin(@RequestBody TokenDto tokenDto) {
        String idTokenString = tokenDto.getIdToken();

        GoogleIdToken.Payload payload = googleVerifierService.verify(idTokenString);
        if (payload == null) {
            throw new BadRequestException("Invalid Google ID token");
        }

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        User user = userRepo.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setRole(UserRole.USER);
            newUser.setEmailVerified(true); // Google already verified the email
            return userRepo.save(newUser);
        });

        String accessToken = jwtTokenProvider.generateToken(user);
        RefreshTokenReadDto newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        UserReadDto userReadDto = userMapper.toUserReadDto(user);

        UserInfoDto userInfoDto = new UserInfoDto();
        userInfoDto.setId(user.getId().toString());
        userInfoDto.setEmail(user.getEmail());
        userInfoDto.setName(user.getName());
        userInfoDto.setRole(user.getRole());
        userInfoDto.setUserPreference(userReadDto.getUserPreference());
        userInfoDto.setToken(accessToken);
        userInfoDto.setRefreshToken(newRefreshToken);
        SuccessResponseEntity<UserInfoDto> body = new SuccessResponseEntity<>();
        body.setData(userInfoDto);
        return ResponseEntity.ok(body);
    }

    // Anyone can refresh their access token using a refresh token
    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponseEntity<UserInfoDto>> refresh(@RequestBody @Valid RefreshTokenRequestDto refreshTokenRequestDto) {
        UserInfoDto userInfo = authService.refreshToken(refreshTokenRequestDto.getRefreshToken());
        SuccessResponseEntity<UserInfoDto> body = new SuccessResponseEntity<>();
        body.setData(userInfo);
        return ResponseEntity.ok(body);
    }

    // Anyone can verify their email with a token
    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        try {
            User user = emailVerificationTokenService.verifyToken(token);
            userRepo.save(user); // Save the updated emailVerified status
            
            // Build URLs
            String webLoginUrl = frontendUrl.replace("/api/v1", "").replaceAll("/$", "") + "/login?verified=true";
            String mobileDeepLink = mobileDeepLinkScheme + "login?verified=true&token=" + token;
            
            // Detect if mobile device
            boolean isMobile = userAgent != null && (userAgent.toLowerCase().contains("mobile") || 
                    userAgent.toLowerCase().contains("android") || 
                    userAgent.toLowerCase().contains("iphone") || 
                    userAgent.toLowerCase().contains("ipad"));
            
            // Load and format success template
            Map<String, String> variables = new HashMap<>();
            variables.put("LOGIN_URL", webLoginUrl);
            variables.put("MOBILE_DEEP_LINK", mobileDeepLink);
            variables.put("IS_MOBILE", String.valueOf(isMobile));
            String html = emailTemplateService.loadAndFormatTemplate("email-verification-success.html", variables);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
                    
        } catch (BadRequestException e) {
            // Build login URL for error page
            String loginUrl = frontendUrl.replace("/api/v1", "").replaceAll("/$", "") + "/login";
            
            // Load and format error template
            Map<String, String> variables = new HashMap<>();
            variables.put("LOGIN_URL", loginUrl);
            variables.put("ERROR_MESSAGE", e.getMessage());
            String errorHtml = emailTemplateService.loadAndFormatTemplate("email-verification-error.html", variables);
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml);
        }
    }

    // Anyone can request a new verification email
    @PostMapping("/resend-verification")
    public ResponseEntity<SuccessResponseEntity<String>> resendVerificationEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            throw new BadRequestException("Email is required");
        }
        
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        
        var token = emailVerificationTokenService.createToken(user);
        emailService.sendVerificationEmail(user.getEmail(), token.getToken(), user.getName());
        
        SuccessResponseEntity<String> body = new SuccessResponseEntity<>();
        body.setData("Verification email sent successfully");
        return ResponseEntity.ok(body);
    }

    // Anyone can request a password reset
    @PostMapping("/forgot-password")
    public ResponseEntity<SuccessResponseEntity<String>> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDto request) {
        authService.forgotPassword(request.getEmail());
        
        SuccessResponseEntity<String> body = new SuccessResponseEntity<>();
        body.setData("Password reset email sent successfully. Please check your inbox.");
        return ResponseEntity.ok(body);
    }

    // Anyone can reset their password with a valid token
    @PostMapping("/reset-password")
    public ResponseEntity<SuccessResponseEntity<String>> resetPassword(@RequestBody @Valid ResetPasswordRequestDto request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        
        SuccessResponseEntity<String> body = new SuccessResponseEntity<>();
        body.setData("Password reset successfully. You can now log in with your new password.");
        return ResponseEntity.ok(body);
    }
}
