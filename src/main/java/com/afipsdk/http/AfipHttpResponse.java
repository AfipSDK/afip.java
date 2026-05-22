package com.afipsdk.http;

/**
 * Respuesta HTTP interna. Solo visible dentro del paquete {@code http}.
 */
final class AfipHttpResponse {

    private final int status;
    private final String statusText;
    private final String data;

    AfipHttpResponse(int status, String statusText, String data) {
        this.status = status;
        this.statusText = statusText;
        this.data = data;
    }

    int getStatus() { return status; }
    String getStatusText() { return statusText; }
    String getData() { return data; }
}
