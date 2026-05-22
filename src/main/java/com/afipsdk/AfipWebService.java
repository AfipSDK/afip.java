package com.afipsdk;

import com.afipsdk.exception.AfipException;
import com.afipsdk.model.GetServiceTAResponse;
import com.afipsdk.model.WebServiceConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase base para los servicios de AFIP. Maneja la comunicación con el backend de AfipSDK.
 */
public class AfipWebService {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    protected final Afip afip;
    protected final String service;
    private final WebServiceConfig options;

    protected boolean isSoapV12() { return false; }
    protected String getWsdl()     { return ""; }
    protected String getUrl()      { return ""; }
    protected String getWsdlTest() { return ""; }
    protected String getUrlTest()  { return ""; }

    /**
     * Constructor principal. Usado tanto por subclases como por web services genéricos.
     * Si {@code options.isGeneric()} es true, valida que {@code options.getService()} no esté vacío
     * y fuerza el uso de SOAP 1.2.
     */
    public AfipWebService(Afip afip, WebServiceConfig options) {
        this.afip = afip;
        this.options = options != null ? options : new WebServiceConfig();
        if (this.options.isGeneric()) {
            if (this.options.getService().isEmpty()) {
                throw new AfipException("service field is required in options");
            }
            if (!this.options.isSoapV12()) {
                this.options.setSoapV12(true);
            }
        }
        this.service = this.options.getService();
    }

    /**
     * Obtiene el token de autorización del servicio AFIP asociado.
     */
    public GetServiceTAResponse getTokenAuthorization() {
        return getTokenAuthorization(false);
    }

    /**
     * Obtiene el token de autorización del servicio AFIP asociado.
     *
     * @param force forzar renovación aunque el token no haya expirado
     */
    public GetServiceTAResponse getTokenAuthorization(boolean force) {
        return afip.getServiceTa(service, force);
    }

    /**
     * Ejecuta una request HTTP directa al backend de AfipSDK y deserializa la respuesta.
     * Para uso interno de subclases que necesitan llamar endpoints REST (no SOAP).
     */
    protected <T> T makeApiRequest(String method, String path, Map<String, Object> body, Class<T> responseType) {
        String json = afip.makeRequest(method, path, body);
        return GSON.fromJson(json, responseType);
    }

    /**
     * Ejecuta un método SOAP del servicio sin parámetros adicionales.
     */
    public Map<String, Object> executeRequest(String method) {
        return executeRequest(method, null);
    }

    /**
     * Ejecuta un método SOAP del servicio con los parámetros dados.
     */
    public Map<String, Object> executeRequest(String method, Map<String, Object> parameters) {
        boolean isProd = afip.getOptions().isProduction();

        String wsdl, url;
        if (isProd) {
            wsdl = !options.getWsdl().isEmpty() ? options.getWsdl() : getWsdl();
            url  = !options.getUrl().isEmpty()  ? options.getUrl()  : getUrl();
        } else {
            wsdl = !options.getWsdlTest().isEmpty() ? options.getWsdlTest() : getWsdlTest();
            url  = !options.getUrlTest().isEmpty()  ? options.getUrlTest()  : getUrlTest();
        }

        Map<String, Object> requestData = new HashMap<String, Object>();
        requestData.put("method", method);
        requestData.put("params", parameters);
        requestData.put("environment", isProd ? "prod" : "dev");
        requestData.put("wsid", service);
        requestData.put("url", url);
        requestData.put("wsdl", wsdl);
        requestData.put("soap_v_1_2", options.isSoapV12() || isSoapV12());

        String json = afip.makeRequest("POST", "v1/afip/requests", requestData);
        return GSON.fromJson(json, MAP_TYPE);
    }
}
