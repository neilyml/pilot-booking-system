package com.aiimglobal.pilot.booking.system.exception;

import java.io.Serial;

public class InvalidCredentialsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Email or password is invalid.");
    }
}
