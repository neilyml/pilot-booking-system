package com.aiimglobal.pilot.booking.system.assignment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignPilotRequest(
        @NotNull @Positive Long pilotId) {
}
