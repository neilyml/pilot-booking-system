package com.aiimglobal.pilot.booking.system.pilot.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePilotRequest(
        @NotBlank @Size(max = 80) String employeeNumber,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 50) String phone,
        @Email @Size(max = 255) String email) {

    public CreatePilotRequest {
        employeeNumber = normalizeEmployeeNumber(employeeNumber);
        name = trim(name);
        phone = trimToNull(phone);
        email = trimToNull(email);
    }

    static String normalizeEmployeeNumber(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
