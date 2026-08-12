package com.aiimglobal.pilot.booking.system.auth.dto;

import java.time.Instant;
import java.util.List;

import com.aiimglobal.pilot.booking.system.user.domain.RoleName;
import com.aiimglobal.pilot.booking.system.user.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public record RegisterOwnerResponse(
        Long id,
        String email,
        @JsonInclude(Include.NON_NULL) String phone,
        String fullName,
        UserStatus status,
        List<RoleName> roles,
        Instant createdAt) {
}
