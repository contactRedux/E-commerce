package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id, Authentication auth) {
        User requester = resolveRequester(auth);
        if (!isAdmin(requester) && !requester.getId().equals(id)) {
            throw new AccessDeniedException("Access denied");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, Authentication auth) {
        User requester = resolveRequester(auth);
        if (!isAdmin(requester) && !requester.getId().equals(id)) {
            throw new AccessDeniedException("Access denied");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id, Authentication auth) {
        User requester = resolveRequester(auth);
        if (!isAdmin(requester) && !requester.getId().equals(id)) {
            throw new AccessDeniedException("Access denied");
        }
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable, Authentication auth) {
        User requester = resolveRequester(auth);
        if (!isAdmin(requester)) {
            throw new AccessDeniedException("Admin access required");
        }
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    private User resolveRequester(Authentication auth) {
        String userId = auth.getName();
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found: " + userId));
    }

    private boolean isAdmin(User user) {
        return Role.ROLE_ADMIN.equals(user.getRole());
    }
}
