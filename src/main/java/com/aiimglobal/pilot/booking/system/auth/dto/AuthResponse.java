package com.aiimglobal.pilot.booking.system.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt) {
}
