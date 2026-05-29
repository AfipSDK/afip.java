package com.afipsdk.service;

import com.afipsdk.Afip;
import com.afipsdk.AfipWebService;
import com.afipsdk.model.GetServiceTAResponse;
import com.afipsdk.model.WebServiceConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de constancia de inscripción de contribuyentes (ws_sr_constancia_inscripcion).
 */
public final class RegisterInscriptionProof extends AfipWebService {

    private static final String WSID = "ws_sr_constancia_inscripcion";

    @Override protected String getWsdl()     { return "ws_sr_padron_a5-production.wsdl"; }
    @Override protected String getUrl()      { return "https://aws.afip.gov.ar/sr-padron/webservices/personaServiceA5"; }
    @Override protected String getWsdlTest() { return "ws_sr_padron_a5.wsdl"; }
    @Override protected String getUrlTest()  { return "https://awshomo.afip.gov.ar/sr-padron/webservices/personaServiceA5"; }

    public RegisterInscriptionProof(Afip afip) {
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

            return executeAfip("getPersona_v2", extra);
        } catch (Exception e) {
            if (isNotFoundError(e)) return null;
            throw e;
        }
    }

    /**
     * Devuelve los datos de múltiples contribuyentes por CUIT/CUIL.
     * Devuelve {@code null} si no existen.
     */
    public List<Map<String, Object>> getTaxpayersDetails(long[] identifiers) {
        try {
            Map<String, Object> extra = new HashMap<String, Object>();
            extra.put("idPersona", identifiers);

            Map<String, Object> result = executeAfip("getPersonaList_v2", extra);
            return extractList(result, "persona");
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
        if ("getPersona_v2".equals(operation)) {
            key = "personaReturn";
        } else if ("getPersonaList_v2".equals(operation)) {
            key = "personaListReturn";
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
    private List<Map<String, Object>> extractList(Map<String, Object> result, String key) {
        Object val = result.get(key);
        if (val instanceof List) return (List<Map<String, Object>>) val;
        return null;
    }
}
