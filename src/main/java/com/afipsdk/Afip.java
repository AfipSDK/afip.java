package com.afipsdk;

import com.afipsdk.exception.AfipException;
import com.afipsdk.http.HttpClient;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.model.AutomationResponse;
import com.afipsdk.model.GetLastRequestXmlResponse;
import com.afipsdk.model.GetServiceTAResponse;
import com.afipsdk.model.WebServiceConfig;
import com.afipsdk.service.ElectronicBilling;
import com.afipsdk.service.RegisterInscriptionProof;
import com.afipsdk.service.RegisterScopeThirteen;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * Punto de entrada del SDK oficial de AfipSDK para Java.
 *
 * <pre>{@code
 * AfipOptions options = new AfipOptions();
 * options.setCuit("20123456789");
 * options.setAccessToken("tu-token");
 * options.setProduction(false);
 *
 * Afip afip = new Afip(options);
 * }</pre>
 */
public final class Afip {

    private static final Gson GSON = new Gson();

    private final AfipOptions options;
    private final HttpClient httpClient;

    private final ElectronicBilling electronicBilling;
    private final RegisterScopeThirteen registerScopeThirteen;
    private final RegisterInscriptionProof registerInscriptionProof;

    public Afip(AfipOptions options) {
        this.options = options != null ? options : new AfipOptions();
        this.httpClient = new HttpClient(this.options);

        this.electronicBilling = new ElectronicBilling(this);
        this.registerScopeThirteen = new RegisterScopeThirteen(this);
        this.registerInscriptionProof = new RegisterInscriptionProof(this);
    }
    // package-private: para tests
    Afip(AfipOptions options, HttpClient httpClient) {
        this.options = options != null ? options : new AfipOptions();
        this.httpClient = httpClient;
        this.electronicBilling = new ElectronicBilling(this);
        this.registerScopeThirteen = new RegisterScopeThirteen(this);
        this.registerInscriptionProof = new RegisterInscriptionProof(this);
    }


    // -------------------------------------------------------------------------
    // Servicios
    // -------------------------------------------------------------------------

    /** Devuelve la configuración usada por este cliente. */
    public AfipOptions getOptions() { return options; }

    /** Servicio de facturación electrónica (WSFE). */
    public ElectronicBilling electronicBilling() { return electronicBilling; }

    /** Servicio del padrón alcance 13. */
    public RegisterScopeThirteen registerScopeThirteen() { return registerScopeThirteen; }

    /** Servicio de constancia de inscripción. */
    public RegisterInscriptionProof registerInscriptionProof() { return registerInscriptionProof; }

    /**
     * Crea un web service genérico para un servicio AFIP no implementado nativamente.
     *
     * <pre>{@code
     * AfipWebService ws = afip.webService("ws_sr_padron_a3");
     * Map<String, Object> result = ws.executeRequest("someMethod", params);
     * }</pre>
     */
    public AfipWebService webService(String service) {
        return webService(service, new WebServiceConfig());
    }

    /**
     * Crea un web service genérico con URLs de WSDL personalizadas.
     */
    public AfipWebService webService(String service, WebServiceConfig options) {
        if (options == null) options = new WebServiceConfig();
        options.setService(service);
        options.setGeneric(true);
        return new AfipWebService(this, options);
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    /**
     * Obtiene el token de autenticación AFIP para un servicio dado.
     *
     * @param service identificador del web service AFIP (ej: "wsfe")
     */
    public GetServiceTAResponse getServiceTa(String service) {
        return getServiceTa(service, false);
    }

    /**
     * Obtiene el token de autenticación AFIP para un servicio dado.
     *
     * @param service identificador del web service AFIP (ej: "wsfe")
     * @param force   forzar renovación aunque el token no haya expirado
     */
    public GetServiceTAResponse getServiceTa(String service, boolean force) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("environment", options.isProduction() ? "prod" : "dev");
        body.put("wsid", service);
        body.put("tax_id", options.getCuit());
        body.put("force_create", force);

        String cert = options.getCert();
        if (cert != null && !cert.isEmpty()) {
            body.put("cert", cert);
        }
        String key = options.getKey();
        if (key != null && !key.isEmpty()) {
            body.put("key", key);
        }

        String json = httpClient.post("v1/afip/auth", body);
        return GSON.fromJson(json, GetServiceTAResponse.class);
    }

    // -------------------------------------------------------------------------
    // Debugging
    // -------------------------------------------------------------------------

    /**
     * Devuelve el último XML de request/response enviado a AFIP.
     * Útil para depuración.
     */
    public GetLastRequestXmlResponse getLastRequestXml() {
        String json = httpClient.get("v1/afip/requests/last-xml");
        return GSON.fromJson(json, GetLastRequestXmlResponse.class);
    }

    // -------------------------------------------------------------------------
    // Automatizaciones
    // -------------------------------------------------------------------------

    /**
     * Crea una automatización y espera hasta que esté completa.
     *
     * @param automation nombre de la automatización
     * @param parameters parámetros de la automatización
     */
    public AutomationResponse createAutomation(String automation, Map<String, Object> parameters) {
        return createAutomation(automation, parameters, true);
    }

    /**
     * Crea una automatización.
     *
     * @param automation nombre de la automatización
     * @param parameters parámetros de la automatización
     * @param wait       si {@code true}, espera hasta que el status sea "complete"
     */
    public AutomationResponse createAutomation(String automation, Map<String, Object> parameters, boolean wait) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("automation", automation);
        body.put("params", parameters);

        String json = httpClient.post("v1/automations", body);
        AutomationResponse result = GSON.fromJson(json, AutomationResponse.class);

        if (!wait || "complete".equalsIgnoreCase(result.getStatus())) {
            return result;
        }

        String id = result.getId();
        if (id == null || id.isEmpty()) {
            return result;
        }

        return getAutomationDetails(id, true);
    }

    /**
     * Consulta el estado de una automatización sin esperar.
     *
     * @param id identificador de la automatización
     */
    public AutomationResponse getAutomationDetails(String id) {
        return getAutomationDetails(id, false);
    }

    /**
     * Consulta el estado de una automatización.
     *
     * @param id   identificador de la automatización
     * @param wait si {@code true}, sigue consultando hasta que el status sea "complete"
     */
    public AutomationResponse getAutomationDetails(String id, boolean wait) {
        if (id == null || id.isEmpty()) {
            throw new AfipException("Automation id is required.");
        }

        int retries = 24;
        while (retries-- >= 0) {
            String json = httpClient.get("v1/automations/" + id);
            AutomationResponse result = GSON.fromJson(json, AutomationResponse.class);

            if (!wait || "complete".equalsIgnoreCase(result.getStatus())) {
                return result;
            }

            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AfipException("Interrupted while waiting for automation.", e);
            }
        }

        throw new AfipException("Error: Waiting for too long.");
    }

    // -------------------------------------------------------------------------
    // HTTP interno
    // -------------------------------------------------------------------------

    /**
     * Ejecuta una request HTTP hacia el backend de AfipSDK.
     * Para uso interno de los servicios.
     *
     * @param method "GET" o "POST"
     * @param path   path relativo a la URL base (ej: "v1/afip/requests")
     * @param body   cuerpo de la request (ignorado en GET)
     * @return JSON de respuesta como String
     */
    String makeRequest(String method, String path, Map<String, Object> body) {
        if ("GET".equals(method)) {
            return httpClient.get(path);
        }
        return httpClient.post(path, body);
    }

}
