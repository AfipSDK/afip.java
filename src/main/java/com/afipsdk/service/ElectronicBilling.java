package com.afipsdk.service;

import com.afipsdk.Afip;
import com.afipsdk.AfipWebService;
import com.afipsdk.exception.AfipWebServiceException;
import com.afipsdk.model.CreatePDFRequest;
import com.afipsdk.model.CreatePDFResponse;
import com.afipsdk.model.GetServiceTAResponse;
import com.afipsdk.model.WebServiceConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de facturación electrónica de AFIP (WSFE).
 */
public final class ElectronicBilling extends AfipWebService {

    private static final String WSID = "wsfe";

    @Override protected boolean isSoapV12() { return true; }
    @Override protected String getWsdl()     { return "wsfe-production.wsdl"; }
    @Override protected String getUrl()      { return "https://servicios1.afip.gov.ar/wsfev1/service.asmx"; }
    @Override protected String getWsdlTest() { return "wsfe.wsdl"; }
    @Override protected String getUrlTest()  { return "https://wswhomo.afip.gov.ar/wsfev1/service.asmx"; }

    public ElectronicBilling(Afip afip) {
        super(afip, new WebServiceConfig(WSID));
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Crea un PDF para un comprobante electrónico.
     */
    public CreatePDFResponse createPDF(CreatePDFRequest data) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("file_name", data.getFileName());
        body.put("template", data.getTemplate());

        if (data.getSendTo() != null && !data.getSendTo().isEmpty()) {
            body.put("send_to", data.getSendTo());
        }

        return makeApiRequest("POST", "v1/pdfs", body, CreatePDFResponse.class);
    }

    /**
     * Devuelve el número del último comprobante autorizado para un punto de venta y tipo.
     */
    public int getLastVoucher(int salesPoint, int type) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("PtoVta", salesPoint);
        params.put("CbteTipo", type);

        Map<String, Object> result = executeAfip("FECompUltimoAutorizado", params);
        Object caeNum = result.get("CbteNro");
        return caeNum instanceof Number ? ((Number) caeNum).intValue() : 0;
    }

    /**
     * Crea un comprobante y devuelve {@code {CAE, CAEFchVto}}.
     */
    public Map<String, Object> createVoucher(Map<String, Object> data) {
        return createVoucher(data, false);
    }

    /**
     * Crea un comprobante.
     *
     * @param returnResponse si {@code true} devuelve la respuesta completa de AFIP
     */
    public Map<String, Object> createVoucher(Map<String, Object> data, boolean returnResponse) {
        data = new HashMap<String, Object>(data);

        int cantReg = toInt(data.get("CbteHasta")) - toInt(data.get("CbteDesde")) + 1;
        int ptoVta   = toInt(data.get("PtoVta"));
        int cbteTipo = toInt(data.get("CbteTipo"));

        data.remove("CantReg");
        data.remove("PtoVta");
        data.remove("CbteTipo");

        wrapIfPresent(data, "Tributos",    "Tributo");
        wrapIfPresent(data, "Iva",         "AlicIva");
        wrapIfPresent(data, "CbtesAsoc",   "CbteAsoc");
        wrapIfPresent(data, "Compradores", "Comprador");
        wrapIfPresent(data, "Opcionales",  "Opcional");

        Map<String, Object> feCabReq = new HashMap<String, Object>();
        feCabReq.put("CantReg", cantReg);
        feCabReq.put("PtoVta", ptoVta);
        feCabReq.put("CbteTipo", cbteTipo);

        Map<String, Object> feDetReq = new HashMap<String, Object>();
        feDetReq.put("FECAEDetRequest", data);

        Map<String, Object> feCAEReq = new HashMap<String, Object>();
        feCAEReq.put("FeCabReq", feCabReq);
        feCAEReq.put("FeDetReq", feDetReq);

        Map<String, Object> req = new HashMap<String, Object>();
        req.put("FeCAEReq", feCAEReq);

        Map<String, Object> result = executeAfip("FECAESolicitar", req);

        if (returnResponse) return result;

        Map<String, Object> detResp = extractDetResponse(result);
        Map<String, Object> response = new HashMap<String, Object>();
        if (detResp != null) {
            response.put("CAE", detResp.get("CAE"));
            Object caeFchVto = detResp.get("CAEFchVto");
            response.put("CAEFchVto", caeFchVto != null ? formatDate(caeFchVto.toString()) : null);
        }
        return response;
    }

    /**
     * Crea el siguiente comprobante correlativo de forma automática.
     * Equivalente a obtener el último número y sumar 1.
     */
    public Map<String, Object> createNextVoucher(Map<String, Object> data) {
        int ptoVta   = toInt(data.get("PtoVta"));
        int cbteTipo = toInt(data.get("CbteTipo"));

        int voucherNumber = getLastVoucher(ptoVta, cbteTipo) + 1;

        data = new HashMap<String, Object>(data);
        data.put("CbteDesde", voucherNumber);
        data.put("CbteHasta", voucherNumber);

        Map<String, Object> res = createVoucher(data);
        res.put("voucherNumber", voucherNumber);
        return res;
    }

    /**
     * Consulta la información de un comprobante. Devuelve {@code null} si no existe.
     */
    public Map<String, Object> getVoucherInfo(int number, int salesPoint, int type) {
        try {
            Map<String, Object> consReq = new HashMap<String, Object>();
            consReq.put("CbteNro", number);
            consReq.put("PtoVta", salesPoint);
            consReq.put("CbteTipo", type);

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("FeCompConsReq", consReq);

            Map<String, Object> result = executeAfip("FECompConsultar", params);
            return extractObject(result, "ResultGet");
        } catch (AfipWebServiceException e) {
            if (e.getCode() == 602) return null;
            throw e;
        }
    }

    /** Solicita un CAEA para el período y quincena dados. */
    public Map<String, Object> createCAEA(int period, int fortnight) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("Periodo", period);
        params.put("Orden", fortnight);
        return extractObject(executeAfip("FECAEASolicitar", params), "ResultGet");
    }

    /** Consulta el CAEA para el período y quincena dados. */
    public Map<String, Object> getCAEA(int period, int fortnight) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("Periodo", period);
        params.put("Orden", fortnight);
        return extractObject(executeAfip("FECAEAConsultar", params), "ResultGet");
    }

    /** Devuelve los puntos de venta habilitados. */
    public List<Map<String, Object>> getSalesPoints() {
        return extractList(executeAfip("FEParamGetPtosVenta"), "ResultGet", "PtoVenta");
    }

    /** Devuelve los tipos de comprobante disponibles. */
    public List<Map<String, Object>> getVoucherTypes() {
        return extractList(executeAfip("FEParamGetTiposCbte"), "ResultGet", "CbteTipo");
    }

    /** Devuelve los tipos de concepto disponibles. */
    public List<Map<String, Object>> getConceptTypes() {
        return extractList(executeAfip("FEParamGetTiposConcepto"), "ResultGet", "ConceptoTipo");
    }

    /** Devuelve los tipos de documento disponibles. */
    public List<Map<String, Object>> getDocumentTypes() {
        return extractList(executeAfip("FEParamGetTiposDoc"), "ResultGet", "DocTipo");
    }

    /** Devuelve las alícuotas de IVA disponibles. */
    public List<Map<String, Object>> getAliquotTypes() {
        return extractList(executeAfip("FEParamGetTiposIva"), "ResultGet", "IvaTipo");
    }

    /** Devuelve los tipos de moneda disponibles. */
    public List<Map<String, Object>> getCurrenciesTypes() {
        return extractList(executeAfip("FEParamGetTiposMonedas"), "ResultGet", "Moneda");
    }

    /** Devuelve las opciones disponibles. */
    public List<Map<String, Object>> getOptionsTypes() {
        return extractList(executeAfip("FEParamGetTiposOpcional"), "ResultGet", "OpcionalTipo");
    }

    /** Devuelve los tipos de tributo disponibles. */
    public List<Map<String, Object>> getTaxTypes() {
        return extractList(executeAfip("FEParamGetTiposTributos"), "ResultGet", "TributoTipo");
    }

    /** Devuelve el estado de los servidores de AFIP. */
    public Map<String, Object> getServerStatus() {
        return executeAfip("FEDummy");
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Ejecuta una operación AFIP agregando automáticamente la autenticación (Token, Sign, Cuit).
     */
    private Map<String, Object> executeAfip(String operation) {
        return executeAfip(operation, null);
    }

    private Map<String, Object> executeAfip(String operation, Map<String, Object> parameters) {
        Map<String, Object> params = parameters != null
                ? new HashMap<String, Object>(parameters)
                : new HashMap<String, Object>();

        if (!"FEDummy".equalsIgnoreCase(operation)) {
            GetServiceTAResponse ta = afip.getServiceTa(WSID);
            Map<String, Object> auth = new HashMap<String, Object>();
            auth.put("Token", ta.getToken());
            auth.put("Sign",  ta.getSign());
            auth.put("Cuit",  afip.getOptions().getCuit());
            params.put("Auth", auth);
        }

        Map<String, Object> result = executeRequest(operation, params);
        checkErrors(operation, result);

        Map<String, Object> extracted = extractObject(result, operation + "Result");
        return extracted != null ? extracted : new HashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private void checkErrors(String operation, Map<String, Object> results) {
        String key = operation + "Result";
        Object resObj = results.get(key);
        if (!(resObj instanceof Map)) return;

        Map<String, Object> res = (Map<String, Object>) resObj;

        if ("FECAESolicitar".equals(operation)) {
            Object feDetRespObj = res.get("FeDetResp");
            if (feDetRespObj instanceof Map) {
                Object detResponseObj = ((Map<String, Object>) feDetRespObj).get("FECAEDetResponse");
                if (detResponseObj instanceof List && ((List<?>) detResponseObj).size() > 1) return;

                Map<String, Object> detResp = extractDetResponse(res);
                if (detResp != null) {
                    Object obs       = detResp.get("Observaciones");
                    Object resultado = detResp.get("Resultado");
                    if (obs instanceof Map && !"A".equals(resultado)) {
                        Object obsErr = ((Map<String, Object>) obs).get("Obs");
                        if (obsErr != null) {
                            throwError(obsErr);
                            return;
                        }
                    }
                }
            }
        }

        Object errorsObj = res.get("Errors");
        if (errorsObj instanceof Map) {
            Object err = ((Map<String, Object>) errorsObj).get("Err");
            if (err != null) throwError(err);
        }
    }

    @SuppressWarnings("unchecked")
    private void throwError(Object err) {
        Map<String, Object> errItem;
        if (err instanceof List) {
            errItem = (Map<String, Object>) ((List<?>) err).get(0);
        } else {
            errItem = (Map<String, Object>) err;
        }
        int code    = ((Number) errItem.get("Code")).intValue();
        Object msgObj = errItem.get("Msg");
        String msg  = msgObj != null ? msgObj.toString() : "";
        throw new AfipWebServiceException("(" + code + ") " + msg, code);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractObject(Map<String, Object> result, String key) {
        Object val = result.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(
            Map<String, Object> result, String key1, String key2) {
        Object val1 = result.get(key1);
        if (!(val1 instanceof Map)) return null;

        Object val2 = ((Map<String, Object>) val1).get(key2);
        if (val2 instanceof List) {
            return (List<Map<String, Object>>) val2;
        } else if (val2 instanceof Map) {
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
            list.add((Map<String, Object>) val2);
            return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDetResponse(Map<String, Object> result) {
        Object feDetRespObj = result.get("FeDetResp");
        if (!(feDetRespObj instanceof Map)) return null;

        Object detResponse = ((Map<String, Object>) feDetRespObj).get("FECAEDetResponse");
        if (detResponse instanceof List) {
            List<?> list = (List<?>) detResponse;
            return list.isEmpty() ? null : (Map<String, Object>) list.get(0);
        } else if (detResponse instanceof Map) {
            return (Map<String, Object>) detResponse;
        }
        return null;
    }

    private static void wrapIfPresent(Map<String, Object> data, String outerKey, String innerKey) {
        if (data.containsKey(outerKey)) {
            Map<String, Object> wrapper = new HashMap<String, Object>();
            wrapper.put(innerKey, data.get(outerKey));
            data.put(outerKey, wrapper);
        }
    }

    private static String formatDate(String date) {
        if (date != null && date.length() == 8) {
            return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
        }
        return date;
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return 0;
    }
}
