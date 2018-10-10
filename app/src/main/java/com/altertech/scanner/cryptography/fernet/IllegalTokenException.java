package com.altertech.scanner.cryptography.fernet;

/**
 * Created by oshevchuk on 10.10.2018
 */
public class IllegalTokenException extends IllegalArgumentException {

    private static final long serialVersionUID = -1794971941479648725L;

    public IllegalTokenException(final String message) {
        super(message);
    }

    public IllegalTokenException(final String message, final Throwable cause) {
        super(message, cause);
    }

}