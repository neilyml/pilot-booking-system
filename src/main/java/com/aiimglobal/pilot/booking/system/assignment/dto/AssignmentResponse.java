package com.aiimglobal.pilot.booking.system.assignment.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.aiimglobal.pilot.booking.system.assignment.domain.AssignmentStatus;

public record AssignmentResponse(
        Long id,
        Long bookingId,
        Long pilotId,
        String pilotEmployeeNumber,
        String pilotName,
        LocalDate serviceDate,
        Long assignedById,
        String assignedByEmail,
        AssignmentStatus status,
        Instant assignedAt,
        Instant completedAt) {
}
