package com.aiimglobal.pilot.booking.system.exception;

import java.io.Serial;

public class MissingReferenceDataException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MissingReferenceDataException(String message) {
        super(message);
    }
}
