package com.aiimglobal.pilot.booking.system.route.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RouteResponse(
        Long id,
        String code,
        String name,
        String origin,
        String destination,
        BigDecimal serviceFee,
        boolean active,
        Long createdById,
        String createdByEmail,
        Instant createdAt,
        Instant updatedAt) {
}
