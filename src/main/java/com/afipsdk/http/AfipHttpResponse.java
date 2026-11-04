package com.afipsdk.http;

/**
 * Respuesta HTTP del backend de AfipSDK: status, texto del status y cuerpo.
 */
public final class AfipHttpResponse {

    private final int status;
    private final String statusText;
    private final String data;

    public AfipHttpResponse(int status, String statusText, String data) {
        this.status = status;
        this.statusText = statusText;
        this.data = data;
    }

    /** Código de status HTTP. */
    public int getStatus() { return status; }

    /** Texto del status HTTP. */
    public String getStatusText() { return statusText; }

    /** Cuerpo de la respuesta. */
    public String getData() { return data; }
}
