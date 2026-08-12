package com.aiimglobal.pilot.booking.system.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.AssignmentSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.PaymentSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.RouteSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.VesselSummary;

public record AdminBookingResponse(
        Long id,
        String bookingNumber,
        Long requestedById,
        String requestedByEmail,
        LocalDate serviceDate,
        BigDecimal serviceFee,
        BookingStatus status,
        VesselSummary vessel,
        RouteSummary route,
        PaymentSummary payment,
        AssignmentSummary assignment,
        Long reviewedById,
        String reviewedByEmail,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
