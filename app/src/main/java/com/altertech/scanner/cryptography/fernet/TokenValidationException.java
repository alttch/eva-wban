package com.altertech.scanner.cryptography.fernet;

public class TokenValidationException extends RuntimeException {

    private static final long serialVersionUID = 5175834607547919885L;

    public TokenValidationException(final String message) {
        super(message);
    }

    public TokenValidationException(final Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public TokenValidationException(final String message, final Throwable cause) {
        super(message, cause);
    }

}