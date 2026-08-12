package com.aiimglobal.pilot.booking.system.pilot.dto;

import java.time.Instant;

import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;

public record PilotResponse(
        Long id,
        String employeeNumber,
        String name,
        String phone,
        String email,
        PilotStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
