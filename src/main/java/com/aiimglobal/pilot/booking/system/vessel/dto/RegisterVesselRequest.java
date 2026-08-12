package com.aiimglobal.pilot.booking.system.vessel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterVesselRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String registrationNumber,
        @NotBlank @Size(max = 80) String vesselType) {

    public RegisterVesselRequest {
        name = trim(name);
        registrationNumber = trim(registrationNumber);
        vesselType = trim(vesselType);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
