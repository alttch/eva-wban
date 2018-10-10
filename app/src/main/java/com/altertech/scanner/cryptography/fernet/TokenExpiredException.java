package com.altertech.scanner.cryptography.fernet;

/**
 * Created by oshevchuk on 10.10.2018
 */
public class TokenExpiredException extends TokenValidationException {

    private static final long serialVersionUID = -8250681539503776783L;

    public TokenExpiredException(final String message) {
        super(message);
    }

    public TokenExpiredException(final Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public TokenExpiredException(final String message, final Throwable cause) {
        super(message, cause);
    }

}