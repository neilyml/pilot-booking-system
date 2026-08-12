package com.aiimglobal.pilot.booking.system.vessel.dto;

import java.time.Instant;

import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;

public record VesselResponse(
        Long id,
        Long ownerId,
        String name,
        String registrationNumber,
        String vesselType,
        VesselStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
