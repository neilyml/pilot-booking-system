package com.aiimglobal.pilot.booking.system.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOwnerRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(min = 7, max = 50) String phone,
        @NotBlank @Size(min = 8, max = 72) String password) {

    public RegisterOwnerRequest {
        fullName = trim(fullName);
        email = trim(email);
        phone = trimToNull(phone);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
