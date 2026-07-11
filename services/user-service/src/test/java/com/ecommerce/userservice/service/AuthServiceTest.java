package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.EmailAlreadyExistsException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // low strength for test speed
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD, "Alice", "Smith");

        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(USER_ID);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("test.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        var response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("test.jwt.token");
        assertThat(response.user().email()).isEqualTo(EMAIL);
        assertThat(response.user().role()).isEqualTo(Role.ROLE_CUSTOMER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_emailAlreadyExists_throwsException() {
        RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD, "Alice", null);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success() {
        String hash = passwordEncoder.encode(RAW_PASSWORD);
        User user = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(hash)
                .firstName("Alice")
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("login.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        var response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertThat(response.token()).isEqualTo("login.jwt.token");
        assertThat(response.user().id()).isEqualTo(USER_ID);
    }

    @Test
    void login_invalidPassword_throwsBadCredentials() {
        String hash = passwordEncoder.encode(RAW_PASSWORD);
        User user = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(hash)
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrongpassword")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_userNotFound_throwsException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
