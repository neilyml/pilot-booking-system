package com.aiimglobal.pilot.booking.system.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus;

public record BookingReportRow(
        String bookingNumber,
        String ownerName,
        String vesselName,
        String vesselRegistrationNumber,
        String routeCode,
        String routeName,
        LocalDate serviceDate,
        BigDecimal serviceFee,
        BookingStatus bookingStatus,
        PaymentStatus paymentStatus,
        String pilotName) {
}
