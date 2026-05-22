package com.afipsdk.model;

/**
 * Token de acceso (TA) obtenido del servicio de autenticación de AFIP (WSAA).
 */
public final class GetServiceTAResponse {

    private String expiration = "";
    private String token = "";
    private String sign = "";

    /** Fecha y hora de expiración del token (ISO string). */
    public String getExpiration() { return expiration; }

    /** Token de autenticación. */
    public String getToken() { return token; }

    /** Firma del token. */
    public String getSign() { return sign; }
}
