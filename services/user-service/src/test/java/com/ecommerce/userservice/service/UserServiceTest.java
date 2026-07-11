package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    private User adminUser;
    private User customerUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);

        adminUser = User.builder()
                .id(ADMIN_ID)
                .email("admin@example.com")
                .passwordHash("hash")
                .role(Role.ROLE_ADMIN)
                .build();

        customerUser = User.builder()
                .id(CUSTOMER_ID)
                .email("customer@example.com")
                .passwordHash("hash")
                .role(Role.ROLE_CUSTOMER)
                .build();
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                ADMIN_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                CUSTOMER_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    @Test
    void getUser_asAdmin_anyUser() {
        User targetUser = User.builder()
                .id(OTHER_USER_ID)
                .email("other@example.com")
                .passwordHash("hash")
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(targetUser));

        var response = userService.getUser(OTHER_USER_ID, adminAuth());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(OTHER_USER_ID);
    }

    @Test
    void getUser_asCustomer_ownProfile() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));

        var response = userService.getUser(CUSTOMER_ID, customerAuth());

        assertThat(response.id()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void getUser_asCustomer_otherProfile_throwsAccessDenied() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));

        assertThatThrownBy(() -> userService.getUser(OTHER_USER_ID, customerAuth()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateUser_success_ownerUpdatesOwnProfile() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new UpdateUserRequest("Bob", "Jones");
        var response = userService.updateUser(CUSTOMER_ID, request, customerAuth());

        assertThat(response.firstName()).isEqualTo("Bob");
        assertThat(response.lastName()).isEqualTo("Jones");
    }

    @Test
    void deleteUser_success_ownerDeletesOwnAccount() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
        when(userRepository.existsById(CUSTOMER_ID)).thenReturn(true);

        assertThatCode(() -> userService.deleteUser(CUSTOMER_ID, customerAuth()))
                .doesNotThrowAnyException();

        verify(userRepository).deleteById(CUSTOMER_ID);
    }

    @Test
    void deleteUser_asCustomer_otherUser_throwsAccessDenied() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));

        assertThatThrownBy(() -> userService.deleteUser(OTHER_USER_ID, customerAuth()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAllUsers_asNonAdmin_throwsAccessDenied() {
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));

        assertThatThrownBy(() -> userService.getAllUsers(
                org.springframework.data.domain.PageRequest.of(0, 10), customerAuth()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateUser_userNotFound_throwsException() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(OTHER_USER_ID,
                new UpdateUserRequest("Name", null), adminAuth()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
