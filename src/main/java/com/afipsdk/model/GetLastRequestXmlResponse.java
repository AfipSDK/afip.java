package com.afipsdk.model;

/**
 * XML del último request/response enviado al web service de AFIP.
 */
public final class GetLastRequestXmlResponse {

    private String request = "";
    private String response = "";

    /** XML del último request enviado a AFIP. */
    public String getRequest() { return request; }

    /** XML del último response recibido de AFIP. */
    public String getResponse() { return response; }
}
