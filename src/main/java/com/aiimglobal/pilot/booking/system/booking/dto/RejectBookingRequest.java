package com.aiimglobal.pilot.booking.system.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBookingRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
