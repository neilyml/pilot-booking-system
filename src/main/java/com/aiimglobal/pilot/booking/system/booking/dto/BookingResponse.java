package com.aiimglobal.pilot.booking.system.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;

public record BookingResponse(
        Long id,
        String bookingNumber,
        Long requestedById,
        LocalDate serviceDate,
        BigDecimal serviceFee,
        BookingStatus status,
        VesselSummary vessel,
        RouteSummary route,
        PaymentSummary payment,
        AssignmentSummary assignment,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public record VesselSummary(
            Long id,
            String name,
            String registrationNumber,
            String vesselType,
            VesselStatus status) {
    }

    public record RouteSummary(
            Long id,
            String code,
            String name,
            String origin,
            String destination) {
    }

    public record PaymentSummary(
            Long id,
            String status,
            BigDecimal amount,
            Instant paidAt) {
    }

    public record AssignmentSummary(
            Long id,
            String status,
            Long pilotId,
            String pilotName) {
    }
}
