package com.aiimglobal.pilot.booking.system.route.dto;

import java.math.BigDecimal;
import java.util.Locale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RouteRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String origin,
        @NotBlank @Size(max = 100) String destination,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal serviceFee) {

    public RouteRequest {
        code = normalizeCode(code);
        name = trim(name);
        origin = trim(origin);
        destination = trim(destination);
    }

    private static String normalizeCode(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
