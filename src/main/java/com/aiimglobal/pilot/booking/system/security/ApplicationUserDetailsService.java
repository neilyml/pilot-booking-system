package com.aiimglobal.pilot.booking.system.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.user.domain.UserStatus;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApplicationUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String email = username.trim().toLowerCase(Locale.ROOT);
        var user = userRepository.findWithRolesByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
        String[] authorities = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName().name())
                .sorted()
                .toArray(String[]::new);
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(user.getStatus() == UserStatus.DISABLED)
                .authorities(authorities)
                .build();
    }
}
