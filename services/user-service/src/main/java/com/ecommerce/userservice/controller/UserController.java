package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.ApiResponse;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(userService.getUser(id, authentication));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return ApiResponse.success(userService.updateUser(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID id, Authentication authentication) {
        userService.deleteUser(id, authentication);
    }

    @GetMapping
    public ApiResponse<Page<UserResponse>> getAllUsers(Pageable pageable, Authentication authentication) {
        return ApiResponse.success(userService.getAllUsers(pageable, authentication));
    }
}
