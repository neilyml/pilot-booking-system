package com.aiimglobal.pilot.booking.system.pilot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdatePilotRequest(
        @NotBlank @Size(max = 80) String employeeNumber,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 50) String phone,
        @Email @Size(max = 255) String email,
        @NotNull @PositiveOrZero Long version) {

    public UpdatePilotRequest {
        employeeNumber = CreatePilotRequest.normalizeEmployeeNumber(employeeNumber);
        name = name == null ? null : name.trim();
        phone = CreatePilotRequest.trimToNull(phone);
        email = CreatePilotRequest.trimToNull(email);
    }
}
