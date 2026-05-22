package com.afipsdk.model;

/**
 * Opciones de configuración para el SDK de AfipSDK.
 */
public final class AfipOptions {

    private String cuit;
    private boolean production;
    private String cert;
    private String key;
    private String accessToken;

    public AfipOptions() {}

    /** CUIT del contribuyente (ej: "20123456789"). */
    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }

    /** {@code true} para apuntar al ambiente de producción de AFIP. */
    public boolean isProduction() { return production; }
    public void setProduction(boolean production) { this.production = production; }

    /** Certificado AFIP en formato PEM. */
    public String getCert() { return cert; }
    public void setCert(String cert) { this.cert = cert; }

    /** Clave privada en formato PEM. */
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    /** Token de acceso de AfipSDK. */
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
