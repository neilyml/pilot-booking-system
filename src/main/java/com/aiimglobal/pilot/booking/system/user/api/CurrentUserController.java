package com.aiimglobal.pilot.booking.system.user.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.user.application.CurrentUserService;
import com.aiimglobal.pilot.booking.system.user.dto.CurrentUserResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    @GetMapping
    CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return currentUserService.getCurrentUser(jwt.getSubject());
    }
}
