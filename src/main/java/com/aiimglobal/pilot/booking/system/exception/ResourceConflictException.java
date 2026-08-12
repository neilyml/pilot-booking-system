package com.aiimglobal.pilot.booking.system.exception;

import java.io.Serial;

import lombok.Getter;

@Getter
public class ResourceConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;

    public ResourceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

}
