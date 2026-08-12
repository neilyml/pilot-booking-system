package com.aiimglobal.pilot.booking.system.vessel.dto;

import java.time.Instant;

import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public record AdminVesselResponse(
        Long id,
        Long ownerId,
        String ownerEmail,
        String name,
        String registrationNumber,
        String vesselType,
        VesselStatus status,
        @JsonInclude(Include.NON_NULL) Long reviewedById,
        @JsonInclude(Include.NON_NULL) String reviewedByEmail,
        @JsonInclude(Include.NON_NULL) Instant reviewedAt,
        @JsonInclude(Include.NON_NULL) String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {
}
