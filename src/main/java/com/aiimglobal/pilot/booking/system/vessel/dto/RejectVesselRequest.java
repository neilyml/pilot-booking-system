package com.aiimglobal.pilot.booking.system.vessel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectVesselRequest(@NotBlank @Size(max = 2_000) String reason) {

    public RejectVesselRequest {
        reason = reason == null ? null : reason.trim();
    }
}
