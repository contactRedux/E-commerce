package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.RefreshToken;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.EmailAlreadyExistsException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.ROLE_CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        String accessToken = jwtService.generateToken(saved);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(saved);

        log.info("User registered userId={}", saved.getId());
        return AuthResponse.of(accessToken, refreshToken.getToken(),
                jwtService.getExpirationMs(), UserResponse.from(saved));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException("No account found for: " + request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("User logged in userId={}", user.getId());
        return AuthResponse.of(accessToken, refreshToken.getToken(),
                jwtService.getExpirationMs(), UserResponse.from(user));
    }

    /**
     * Issues a new access token + rotated refresh token given a valid refresh token.
     * The old refresh token is revoked on use (rotation).
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
        User user = oldToken.getUser();

        // Revoke the used token and issue a new pair
        oldToken.setRevoked(true);
        String newAccessToken = jwtService.generateToken(user);
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Token refreshed userId={}", user.getId());
        return AuthResponse.of(newAccessToken, newRefreshToken.getToken(),
                jwtService.getExpirationMs(), UserResponse.from(user));
    }

    /**
     * Revokes the user's refresh token (logout).
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenService.revokeToken(refreshTokenValue);
    }
}
