package com.aiimglobal.pilot.booking.system.auth.application;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.auth.dto.RegisterOwnerRequest;
import com.aiimglobal.pilot.booking.system.auth.dto.RegisterOwnerResponse;
import com.aiimglobal.pilot.booking.system.exception.MissingReferenceDataException;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.user.domain.Role;
import com.aiimglobal.pilot.booking.system.user.domain.RoleName;
import com.aiimglobal.pilot.booking.system.user.domain.User;
import com.aiimglobal.pilot.booking.system.user.persistence.RoleRepository;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterOwnerResponse registerOwner(RegisterOwnerRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String phone = request.phone();
        rejectDuplicates(email, phone);

        User user = User.registerOwner(
                email,
                phone,
                passwordEncoder.encode(request.password()),
                request.fullName());

        try {
            User saved = userRepository.saveAndFlush(user);
            Role ownerRole = roleRepository.findByName(RoleName.OWNER)
                    .orElseThrow(() -> new MissingReferenceDataException("OWNER role is not configured."));
            saved.grant(ownerRole);
            userRepository.flush();
            return new RegisterOwnerResponse(
                    saved.getId(),
                    saved.getEmail(),
                    saved.getPhone(),
                    saved.getFullName(),
                    saved.getStatus(),
                    List.of(RoleName.OWNER),
                    saved.getCreatedAt());
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "REGISTRATION_CONFLICT", "The email or phone is already registered.");
        }
    }

    private void rejectDuplicates(String email, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new ResourceConflictException("EMAIL_ALREADY_REGISTERED", "The email is already registered.");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ResourceConflictException("PHONE_ALREADY_REGISTERED", "The phone is already registered.");
        }
    }
}
