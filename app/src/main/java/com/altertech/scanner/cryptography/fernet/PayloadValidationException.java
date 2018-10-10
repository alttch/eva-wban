package com.altertech.scanner.cryptography.fernet;

/**
 * Created by oshevchuk on 10.10.2018
 */
public class PayloadValidationException extends TokenValidationException {

    private static final long serialVersionUID = -2067765218609208844L;

    public PayloadValidationException(final String message) {
        super(message);
    }

    public PayloadValidationException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public PayloadValidationException(final String message, final Throwable cause) {
        super(message, cause);
    }

}