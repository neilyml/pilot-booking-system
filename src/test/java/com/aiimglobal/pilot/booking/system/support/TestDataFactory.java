package com.aiimglobal.pilot.booking.system.support;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.aiimglobal.pilot.booking.system.user.domain.RoleName;
import com.aiimglobal.pilot.booking.system.user.domain.User;
import com.aiimglobal.pilot.booking.system.user.persistence.RoleRepository;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

public final class TestDataFactory {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    TestDataFactory(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createAdmin(String email, String password) {
        var adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is not configured."));
        var admin = User.createActive(
                email,
                null,
                passwordEncoder.encode(password),
                "System Administrator");
        admin.grant(adminRole);
        return userRepository.saveAndFlush(admin);
    }
}
