package com.aiimglobal.pilot.booking.system.user.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.user.domain.Role;
import com.aiimglobal.pilot.booking.system.user.dto.CurrentUserResponse;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String authenticatedEmail) {
        var user = userRepository.findWithRolesByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist."));
        var roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .toList();
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt());
    }
}
