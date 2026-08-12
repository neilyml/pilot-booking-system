package com.aiimglobal.pilot.booking.system.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        @JsonInclude(Include.NON_EMPTY) List<ApiFieldError> fieldErrors) {
}
