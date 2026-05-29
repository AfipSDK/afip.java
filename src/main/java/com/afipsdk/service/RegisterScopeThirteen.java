package com.afipsdk.service;

import com.afipsdk.Afip;
import com.afipsdk.AfipWebService;
import com.afipsdk.model.GetServiceTAResponse;
import com.afipsdk.model.WebServiceConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio del padrón de contribuyentes alcance 13 (ws_sr_padron_a13).
 */
public final class RegisterScopeThirteen extends AfipWebService {

    private static final String WSID = "ws_sr_padron_a13";

    @Override protected String getWsdl()     { return "ws_sr_padron_a13-production.wsdl"; }
    @Override protected String getUrl()      { return "https://aws.afip.gov.ar/sr-padron/webservices/personaServiceA13"; }
    @Override protected String getWsdlTest() { return "ws_sr_padron_a13.wsdl"; }
    @Override protected String getUrlTest()  { return "https://awshomo.afip.gov.ar/sr-padron/webservices/personaServiceA13"; }

    public RegisterScopeThirteen(Afip afip) {
        super(afip, new WebServiceConfig(WSID));
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /** Devuelve el estado de los servidores. */
    public Map<String, Object> getServerStatus() {
        return executeAfip("dummy", null);
    }

    /**
     * Devuelve los datos de un contribuyente por CUIT/CUIL.
     * Devuelve {@code null} si no existe.
     */
    public Map<String, Object> getTaxpayerDetails(long identifier) {
        try {
            Map<String, Object> extra = new HashMap<String, Object>();
            extra.put("idPersona", identifier);

            Map<String, Object> result = executeAfip("getPersona", extra);
            return extractPersona(result);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("No existe") || e.getMessage().contains("es inexistente"))) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Devuelve el CUIT/CUIL correspondiente a un número de documento.
     * Devuelve {@code null} si no existe.
     */
    public Object getTaxIDByDocument(long documentNumber) {
        try {
            Map<String, Object> extra = new HashMap<String, Object>();
            extra.put("documento", documentNumber);

            Map<String, Object> result = executeAfip("getIdPersonaListByDocumento", extra);
            return result.get("idPersona");
        } catch (Exception e) {
            if (isNotFoundError(e)) return null;
            throw e;
        }
    }

    private static boolean isNotFoundError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("No existe") || msg.contains("es inexistente");
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    private Map<String, Object> executeAfip(String operation, Map<String, Object> extraParams) {
        Map<String, Object> params = new HashMap<String, Object>();

        if (!"dummy".equalsIgnoreCase(operation)) {
            GetServiceTAResponse ta = afip.getServiceTa(WSID);
            params.put("token", ta.getToken());
            params.put("sign",  ta.getSign());
            params.put("cuitRepresentada", afip.getOptions().getCuit());

            if (extraParams != null) {
                params.putAll(extraParams);
            }
        }

        Map<String, Object> result = executeRequest(operation, params);

        String key;
        if ("getPersona".equals(operation)) {
            key = "personaReturn";
        } else if ("getIdPersonaListByDocumento".equals(operation)) {
            key = "idPersonaListReturn";
        } else {
            key = "return";
        }

        Object val = result.get(key);
        if (!(val instanceof Map)) return new HashMap<String, Object>();

        @SuppressWarnings("unchecked")
        Map<String, Object> extracted = (Map<String, Object>) val;
        return extracted;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPersona(Map<String, Object> result) {
        Object val = result.get("persona");
        if (val instanceof Map) return (Map<String, Object>) val;
        return null;
    }
}
