package com.afipsdk.exception;

/**
 * Excepción base del SDK de AfipSDK.
 */
public class AfipException extends RuntimeException {

    public AfipException(String message) {
        super(message);
    }

    public AfipException(String message, Throwable cause) {
        super(message, cause);
    }
}
