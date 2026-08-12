package com.aiimglobal.pilot.booking.system.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Current user", description = "Profile of the authenticated user.")
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "Get the current user")
    CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return currentUserService.getCurrentUser(jwt.getSubject());
    }
}
