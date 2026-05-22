package com.afipsdk.exception;

/**
 * Excepción lanzada cuando el web service de AFIP devuelve un error con código.
 */
public class AfipWebServiceException extends AfipException {

    private final int code;

    public AfipWebServiceException(String message, int code) {
        super(message);
        this.code = code;
    }

    /** Código de error devuelto por AFIP. */
    public int getCode() { return code; }
}
