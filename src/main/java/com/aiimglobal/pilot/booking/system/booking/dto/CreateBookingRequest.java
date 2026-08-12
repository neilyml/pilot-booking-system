package com.aiimglobal.pilot.booking.system.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(
        @NotNull @Positive Long vesselId,
        @NotNull @Positive Long routeId,
        @NotNull @Future LocalDate serviceDate) {
}
