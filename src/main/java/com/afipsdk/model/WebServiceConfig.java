package com.afipsdk.model;

/**
 * Configuración para un web service de AFIP.
 * Usada tanto por servicios nativos del SDK como por web services genéricos vía {@code Afip.webService()}.
 */
public final class WebServiceConfig {

    private String wsdl = "";
    private String url = "";
    private String wsdlTest = "";
    private String urlTest = "";
    private String service = "";
    private boolean generic = false;
    private boolean soapV12 = false;

    public WebServiceConfig() {}

    /** Constructor de conveniencia que setea el identificador del servicio AFIP (ej: "wsfe"). */
    public WebServiceConfig(String service) {
        this.service = service != null ? service : "";
    }

    /** WSDL de producción. */
    public String getWsdl() { return wsdl; }
    public void setWsdl(String wsdl) { this.wsdl = wsdl != null ? wsdl : ""; }

    /** URL de producción. */
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url != null ? url : ""; }

    /** WSDL de testing (homologación). */
    public String getWsdlTest() { return wsdlTest; }
    public void setWsdlTest(String wsdlTest) { this.wsdlTest = wsdlTest != null ? wsdlTest : ""; }

    /** URL de testing (homologación). */
    public String getUrlTest() { return urlTest; }
    public void setUrlTest(String urlTest) { this.urlTest = urlTest != null ? urlTest : ""; }

    /** Identificador del servicio AFIP (ej: "wsfe", "ws_sr_padron_a13"). */
    public String getService() { return service; }
    public void setService(String service) { this.service = service != null ? service : ""; }

    /** Indica si es un web service genérico creado con {@code Afip.webService()}. */
    public boolean isGeneric() { return generic; }
    public void setGeneric(boolean generic) { this.generic = generic; }

    /** Indica si el servicio usa SOAP 1.2. */
    public boolean isSoapV12() { return soapV12; }
    public void setSoapV12(boolean soapV12) { this.soapV12 = soapV12; }
}
