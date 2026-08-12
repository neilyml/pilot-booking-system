package com.aiimglobal.pilot.booking.system.auth.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.auth.application.AuthService;
import com.aiimglobal.pilot.booking.system.auth.dto.AuthResponse;
import com.aiimglobal.pilot.booking.system.auth.dto.LoginRequest;
import com.aiimglobal.pilot.booking.system.auth.dto.RegisterOwnerRequest;
import com.aiimglobal.pilot.booking.system.auth.dto.RegisterOwnerResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    ResponseEntity<RegisterOwnerResponse> registerOwner(@Valid @RequestBody RegisterOwnerRequest request) {
        RegisterOwnerResponse response = authService.registerOwner(request);
        return ResponseEntity.created(URI.create("/api/v1/auth/register/" + response.id())).body(response);
    }
}
