package com.afipsdk.service;

import com.afipsdk.Afip;
import com.afipsdk.exception.AfipException;
import com.afipsdk.exception.FacturaSDKException;
import com.afipsdk.http.AfipHttpResponse;
import com.afipsdk.http.HttpClient;
import com.afipsdk.model.AutomationResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Factura SDK: emisión de comprobantes de punta a punta.
 *
 * Emisores, certificados, comprobantes y webhooks de un proyecto Factura SDK. El access token
 * (fsk_dev_… para el sandbox, fsk_prod_… para producción) decide el ambiente.
 *
 * Las entidades se devuelven como {@code Map<String, Object>} tal como las responde la API
 * (https://afipsdk.com/docs/facturasdk/); las automatizaciones como {@link AutomationResponse}.
 *
 * <pre>{@code
 * Map<String, Object> invoice = afip.facturaSDK().createInvoice(data, "venta-0001");
 * Map<String, Object> decided = afip.facturaSDK().getInvoice((String) invoice.get("id"), true);
 * }</pre>
 */
public final class FacturaSDK {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    // Las automatizaciones (ARCA con la clave fiscal, verificación) tardan de segundos a un par de
    // minutos; un comprobante se autoriza en segundo plano en segundos
    private static final long AUTOMATION_INTERVAL_MS = 3_000;
    private static final int AUTOMATION_ATTEMPTS = 60;
    private static final long INVOICE_INTERVAL_MS = 2_000;
    private static final int INVOICE_ATTEMPTS = 60;

    private final Afip afip;
    private final HttpClient httpClient;

    /**
     * Constructor de uso interno: lo crea {@link Afip} con el cliente HTTP del token de Factura SDK.
     */
    public FacturaSDK(Afip afip, HttpClient httpClient) {
        this.afip = afip;
        this.httpClient = httpClient;
    }

    // -------------------------------------------------------------------------
    // Comprobantes
    // -------------------------------------------------------------------------

    /** Emite un comprobante. Ver {@link #createInvoice(Map, String, boolean)}. */
    public Map<String, Object> createInvoice(Map<String, Object> data) {
        return createInvoice(data, null, false);
    }

    /** Emite un comprobante con su Idempotency-Key. Ver {@link #createInvoice(Map, String, boolean)}. */
    public Map<String, Object> createInvoice(Map<String, Object> data, String idempotencyKey) {
        return createInvoice(data, idempotencyKey, false);
    }

    /**
     * Emite un comprobante.
     *
     * Por defecto Factura SDK responde enseguida con el comprobante en cola ({@code queued}) y lo
     * autoriza en ARCA en segundo plano: el resultado llega a tu webhook. Con {@code wait} consulta
     * el comprobante hasta que quede decidido; con {@code "sync": true} en los datos Factura SDK
     * lo decide dentro de la solicitud.
     *
     * @param data           datos del comprobante (https://afipsdk.com/docs/facturasdk/invoices)
     * @param idempotencyKey identificador de la venta en tu sistema, o {@code null}: repetirlo
     *                       devuelve el mismo comprobante en vez de emitir otro
     * @param wait           esperar hasta que el comprobante quede approved, rejected o failed
     * @return el comprobante
     */
    public Map<String, Object> createInvoice(Map<String, Object> data, String idempotencyKey, boolean wait) {
        Map<String, String> headers = null;
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            headers = new HashMap<String, String>();
            headers.put("Idempotency-Key", idempotencyKey);
        }

        Map<String, Object> invoice = request("POST", "invoices", data, null, headers);

        if (wait && isPending(invoice)) {
            return waitForInvoice((String) invoice.get("id"));
        }
        return invoice;
    }

    /** Devuelve un comprobante con su estado actual. */
    public Map<String, Object> getInvoice(String id) {
        return getInvoice(id, false);
    }

    /**
     * Devuelve un comprobante con su estado actual.
     *
     * @param wait esperar hasta que quede approved, rejected o failed
     */
    public Map<String, Object> getInvoice(String id, boolean wait) {
        if (wait) {
            return waitForInvoice(id);
        }
        return request("GET", "invoices/" + id, null, null, null);
    }

    /**
     * Espera a que un comprobante quede decidido: approved, rejected o failed.
     *
     * Consulta el comprobante cada 2 segundos durante 2 minutos como máximo. Si la espera termina,
     * el comprobante sigue su curso en Factura SDK y el resultado llega igual a tu webhook.
     */
    public Map<String, Object> waitForInvoice(String id) {
        int attempts = INVOICE_ATTEMPTS;
        while (attempts-- > 0) {
            Map<String, Object> invoice = request("GET", "invoices/" + id, null, null, null);
            if (!isPending(invoice)) {
                return invoice;
            }
            sleep(INVOICE_INTERVAL_MS);
        }
        throw new AfipException("Error: Waiting for too long.");
    }

    /** Lista los comprobantes, del más nuevo al más viejo. */
    public Map<String, Object> getInvoices() {
        return getInvoices(null);
    }

    /**
     * Lista los comprobantes, del más nuevo al más viejo, con paginación por cursor.
     *
     * @param params filtros: issuer_cuit, status, invoice_type, start, end, limit, cursor
     * @return {@code data}, {@code next_cursor} y {@code total_count}
     */
    public Map<String, Object> getInvoices(Map<String, Object> params) {
        return request("GET", "invoices", null, params, null);
    }

    /** Cancela un comprobante que todavía está en cola. Nunca se emite. */
    public Map<String, Object> cancelInvoice(String id) {
        return request("POST", "invoices/" + id + "/cancel", null, null, null);
    }

    // -------------------------------------------------------------------------
    // Emisores
    // -------------------------------------------------------------------------

    /** Crea un emisor: un CUIT que factura. Nace pending y emite cuando se verifica. */
    public Map<String, Object> createIssuer(Map<String, Object> data) {
        return request("POST", "issuers", data, null, null);
    }

    /** Devuelve un emisor. */
    public Map<String, Object> getIssuer(String id) {
        return request("GET", "issuers/" + id, null, null, null);
    }

    /** Lista los emisores. */
    public Map<String, Object> getIssuers() {
        return getIssuers(null);
    }

    /**
     * Lista los emisores, con paginación por cursor.
     *
     * @param params filtros: tax_id, certificate_id, status, limit, cursor
     */
    public Map<String, Object> getIssuers(Map<String, Object> params) {
        return request("GET", "issuers", null, params, null);
    }

    /** Modifica un emisor: solo cambian los campos enviados. */
    public Map<String, Object> updateIssuer(String id, Map<String, Object> data) {
        return request("PUT", "issuers/" + id, data, null, null);
    }

    /** Borra un emisor. Sus comprobantes quedan. */
    public void deleteIssuer(String id) {
        request("DELETE", "issuers/" + id, null, null, null);
    }

    /**
     * Devuelve los datos de la constancia de inscripción de un CUIT en ARCA, para dar de alta el
     * emisor sin cargarlos a mano.
     */
    public Map<String, Object> lookupIssuer(String taxId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tax_id", taxId);
        return request("GET", "issuers/lookup", null, params, null);
    }

    /** Lista los puntos de venta de un CUIT en ARCA y espera el resultado. */
    public AutomationResponse getSalesPoints(Map<String, Object> data) {
        return getSalesPoints(data, true);
    }

    /**
     * Lista los puntos de venta de un CUIT en ARCA, con su clave fiscal (tax_id, username,
     * password). Es una automatización: con {@code wait} la devuelve terminada, con los puntos de
     * venta en {@code data}.
     */
    public AutomationResponse getSalesPoints(Map<String, Object> data, boolean wait) {
        return automation(request("POST", "issuers/sales-points", data, null, null), wait);
    }

    /** Crea en ARCA un punto de venta de web services y espera el resultado. */
    public AutomationResponse createSalesPoint(Map<String, Object> data) {
        return createSalesPoint(data, true);
    }

    /**
     * Crea en ARCA un punto de venta de web services para un CUIT, con su clave fiscal (tax_id,
     * username, password y opcionalmente vat_condition, number, name, system). Es una
     * automatización: con {@code wait} la devuelve terminada, con el punto de venta en {@code data}.
     */
    public AutomationResponse createSalesPoint(Map<String, Object> data, boolean wait) {
        return automation(request("POST", "issuers/sales-points/create", data, null, null), wait);
    }

    /** Crea un certificado para el emisor y se lo asigna. Ver {@link #createIssuerCertificate(String, Map, boolean)}. */
    public Map<String, Object> createIssuerCertificate(String id, Map<String, Object> data) {
        return createIssuerCertificate(id, data, true);
    }

    /**
     * Crea un certificado para el emisor y se lo asigna.
     *
     * Subido (cert y key en PEM) responde el certificado. Creado en ARCA (source "automation", con
     * username y password) es una automatización: con {@code wait} devuelve la automatización
     * terminada ({@code id}, {@code status}, {@code data} con el certificado).
     */
    public Map<String, Object> createIssuerCertificate(String id, Map<String, Object> data, boolean wait) {
        return automationAsMap(request("POST", "issuers/" + id + "/certificate", data, null, null), wait);
    }

    /** Acepta la delegación del emisor y espera el resultado. */
    public AutomationResponse createIssuerDelegation(String id, Map<String, Object> data) {
        return createIssuerDelegation(id, data, true);
    }

    /**
     * Acepta en ARCA la delegación del emisor al dueño de su certificado y autoriza el certificado
     * para facturar por él, con la clave fiscal del dueño del certificado (username, password). Es
     * una automatización: con {@code wait} la devuelve terminada.
     */
    public AutomationResponse createIssuerDelegation(String id, Map<String, Object> data, boolean wait) {
        return automation(request("POST", "issuers/" + id + "/delegation", data, null, null), wait);
    }

    /** Verifica un emisor y espera el resultado. */
    public AutomationResponse verifyIssuer(String id) {
        return verifyIssuer(id, true);
    }

    /**
     * Verifica un emisor: emite una factura de $1 y la nota de crédito que la anula, y el emisor
     * pasa a active. En producción los dos comprobantes son reales. Es una automatización: con
     * {@code wait} la devuelve terminada, con verified, issuer, invoice y credit_note en {@code data}.
     */
    public AutomationResponse verifyIssuer(String id, boolean wait) {
        return automation(request("POST", "issuers/" + id + "/verify", null, null, null), wait);
    }

    // -------------------------------------------------------------------------
    // Certificados
    // -------------------------------------------------------------------------

    /** Agrega un certificado al proyecto. Ver {@link #createCertificate(Map, boolean)}. */
    public Map<String, Object> createCertificate(Map<String, Object> data) {
        return createCertificate(data, true);
    }

    /**
     * Agrega un certificado al proyecto.
     *
     * Subido (cert y key en PEM) responde el certificado. Creado en ARCA (source "automation", con
     * tax_id, username y password) es una automatización: con {@code wait} devuelve la
     * automatización terminada ({@code id}, {@code status}, {@code data} con el certificado).
     */
    public Map<String, Object> createCertificate(Map<String, Object> data, boolean wait) {
        return automationAsMap(request("POST", "certificates", data, null, null), wait);
    }

    /** Devuelve un certificado. */
    public Map<String, Object> getCertificate(String id) {
        return request("GET", "certificates/" + id, null, null, null);
    }

    /** Lista los certificados. */
    public Map<String, Object> getCertificates() {
        return getCertificates(null);
    }

    /**
     * Lista los certificados, con paginación por cursor.
     *
     * @param params filtros: tax_id, status, limit, cursor
     */
    public Map<String, Object> getCertificates(Map<String, Object> params) {
        return request("GET", "certificates", null, params, null);
    }

    /** Revoca un certificado: no firma más. */
    public Map<String, Object> revokeCertificate(String id) {
        return request("POST", "certificates/" + id + "/revoke", null, null, null);
    }

    /** Autoriza un certificado para wsfe y espera el resultado. */
    public AutomationResponse authorizeCertificate(String id, Map<String, Object> data) {
        return authorizeCertificate(id, data, true);
    }

    /**
     * Autoriza en ARCA un certificado para el web service de facturación (wsfe), con la clave
     * fiscal de su dueño (password y opcionalmente username). Es una automatización: con
     * {@code wait} la devuelve terminada.
     */
    public AutomationResponse authorizeCertificate(String id, Map<String, Object> data, boolean wait) {
        return automation(request("POST", "certificates/" + id + "/authorize", data, null, null), wait);
    }

    // -------------------------------------------------------------------------
    // Webhooks
    // -------------------------------------------------------------------------

    /**
     * Crea el endpoint de webhook (url): la URL a la que Factura SDK le manda el resultado de cada
     * comprobante. La respuesta trae el secret, que se muestra solo esta vez.
     */
    public Map<String, Object> createWebhookEndpoint(Map<String, Object> data) {
        return request("POST", "webhook-endpoints", data, null, null);
    }

    /** Devuelve un endpoint de webhook. */
    public Map<String, Object> getWebhookEndpoint(String id) {
        return request("GET", "webhook-endpoints/" + id, null, null, null);
    }

    /** Lista los endpoints de webhook. */
    public Map<String, Object> getWebhookEndpoints() {
        return getWebhookEndpoints(null);
    }

    /**
     * Lista los endpoints de webhook.
     *
     * @param params filtros: status
     */
    public Map<String, Object> getWebhookEndpoints(Map<String, Object> params) {
        return request("GET", "webhook-endpoints", null, params, null);
    }

    /** Modifica un endpoint de webhook: url o status (active o inactive). */
    public Map<String, Object> updateWebhookEndpoint(String id, Map<String, Object> data) {
        return request("PUT", "webhook-endpoints/" + id, data, null, null);
    }

    /** Genera un secret nuevo para un endpoint de webhook. Desde entonces cada entrega se firma con él. */
    public Map<String, Object> rotateWebhookEndpointSecret(String id) {
        return request("POST", "webhook-endpoints/" + id + "/rotate", null, null, null);
    }

    /** Manda un evento de prueba firmado a un endpoint de webhook. */
    public Map<String, Object> testWebhookEndpoint(String id) {
        return request("POST", "webhook-endpoints/" + id + "/test", null, null, null);
    }

    /** Lista las entregas de un endpoint de webhook. */
    public Map<String, Object> getWebhookDeliveries(String id) {
        return getWebhookDeliveries(id, null);
    }

    /**
     * Lista las entregas de un endpoint de webhook: qué se mandó, cuándo y con qué resultado.
     *
     * @param params filtros: status, invoice_id, limit, cursor
     */
    public Map<String, Object> getWebhookDeliveries(String id, Map<String, Object> params) {
        return request("GET", "webhook-endpoints/" + id + "/deliveries", null, params, null);
    }

    /** Verifica la firma de una entrega de webhook con la tolerancia por defecto de 5 minutos. */
    public boolean verifyWebhookSignature(String signature, String body, String secret) {
        return verifyWebhookSignature(signature, body, secret, 300);
    }

    /**
     * Verifica la firma de una entrega de webhook.
     *
     * El header X-FacturaSDK-Signature es "t=&lt;timestamp&gt;,v1=&lt;firma&gt;", donde v1 es el
     * HMAC-SHA256 de "&lt;timestamp&gt;.&lt;cuerpo&gt;" con el secret del endpoint. El cuerpo tiene
     * que ser el crudo recibido, byte a byte.
     *
     * @param toleranceSeconds segundos que el timestamp puede diferir de la hora actual
     */
    public boolean verifyWebhookSignature(String signature, String body, String secret, long toleranceSeconds) {
        return verifyWebhookSignature(signature, body != null ? body.getBytes(StandardCharsets.UTF_8) : null, secret, toleranceSeconds);
    }

    /** Verifica la firma de una entrega de webhook a partir del cuerpo en bytes. */
    public boolean verifyWebhookSignature(String signature, byte[] body, String secret, long toleranceSeconds) {
        if (signature == null || body == null || secret == null || secret.isEmpty()) {
            return false;
        }

        Map<String, String> parts = new HashMap<String, String>();
        for (String part : signature.split(",")) {
            String[] pair = part.split("=", 2);
            parts.put(pair[0], pair.length > 1 ? pair[1] : "");
        }

        String t = parts.get("t");
        if (t == null || !t.matches("\\d{1,18}")) {
            return false;
        }
        long timestamp = Long.parseLong(t);
        String received = parts.containsKey("v1") ? parts.get("v1") : "";

        String expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            expected = hex(mac.doFinal());
        } catch (Exception e) {
            throw new AfipException("Error computing the webhook signature: " + e.getMessage(), e);
        }

        boolean fresh = Math.abs(System.currentTimeMillis() / 1000 - timestamp) <= toleranceSeconds;

        return fresh && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    /** Verifica la firma de una entrega y devuelve el evento. Ver {@link #constructWebhookEvent(String, String, String, long)}. */
    public Map<String, Object> constructWebhookEvent(String body, String signature, String secret) {
        return constructWebhookEvent(body, signature, secret, 300);
    }

    /**
     * Verifica la firma de una entrega de webhook y devuelve el evento (event_id, type, created, data).
     *
     * @throws FacturaSDKException si la firma no es válida
     */
    public Map<String, Object> constructWebhookEvent(String body, String signature, String secret, long toleranceSeconds) {
        if (!verifyWebhookSignature(signature, body, secret, toleranceSeconds)) {
            throw new FacturaSDKException("La firma del webhook no es válida");
        }
        return GSON.fromJson(body, MAP_TYPE);
    }

    // -------------------------------------------------------------------------
    // Automatizaciones
    // -------------------------------------------------------------------------

    /** Devuelve una automatización sin esperar. */
    public AutomationResponse getAutomation(String id) {
        return getAutomation(id, false);
    }

    /**
     * Devuelve una automatización.
     *
     * @param wait esperar hasta que termine
     * @throws FacturaSDKException si la automatización terminó con error
     */
    public AutomationResponse getAutomation(String id, boolean wait) {
        if (wait) {
            return waitForAutomation(id);
        }
        return toAutomation(request("GET", "automations/" + id, null, null, null));
    }

    /**
     * Espera a que termine una automatización. La consulta cada 3 segundos durante 3 minutos como máximo.
     *
     * @throws FacturaSDKException si la automatización terminó con error
     */
    public AutomationResponse waitForAutomation(String id) {
        int attempts = AUTOMATION_ATTEMPTS;
        while (attempts-- > 0) {
            Map<String, Object> automation = request("GET", "automations/" + id, null, null, null);
            if (!"in_process".equals(automation.get("status"))) {
                return toAutomation(automation);
            }
            sleep(AUTOMATION_INTERVAL_MS);
        }
        throw new AfipException("Error: Waiting for too long.");
    }

    // -------------------------------------------------------------------------
    // Privados
    // -------------------------------------------------------------------------

    /**
     * Resuelve la respuesta de un endpoint que inicia una automatización (202 con automation_id):
     * con {@code wait} la espera terminada; si no, devuelve la automatización recién iniciada.
     */
    private AutomationResponse automation(Map<String, Object> result, boolean wait) {
        Object automationId = result != null ? result.get("automation_id") : null;
        if (automationId == null) {
            return toAutomation(result);
        }
        if (wait) {
            return waitForAutomation(automationId.toString());
        }
        Map<String, Object> started = new HashMap<String, Object>();
        started.put("id", automationId.toString());
        started.put("status", result.get("status"));
        return toAutomation(started);
    }

    /**
     * Igual que {@link #automation(Map, boolean)} para endpoints que pueden responder una entidad
     * (un certificado subido) o iniciar una automatización: la automatización se devuelve como Map.
     */
    private Map<String, Object> automationAsMap(Map<String, Object> result, boolean wait) {
        if (wait && result != null && result.get("automation_id") != null) {
            AutomationResponse automation = waitForAutomation(result.get("automation_id").toString());
            return GSON.fromJson(GSON.toJson(automation), MAP_TYPE);
        }
        return result;
    }

    private static AutomationResponse toAutomation(Map<String, Object> map) {
        return GSON.fromJson(GSON.toJson(map != null ? map : new HashMap<String, Object>()), AutomationResponse.class);
    }

    private static boolean isPending(Map<String, Object> invoice) {
        Object status = invoice != null ? invoice.get("status") : null;
        return "queued".equals(status) || "processing".equals(status);
    }

    /**
     * Ejecuta una request a la API de Factura SDK.
     *
     * @throws FacturaSDKException si la API responde un error
     */
    private Map<String, Object> request(String method, String path, Map<String, Object> body,
                                        Map<String, Object> params, Map<String, String> headers) {
        AfipHttpResponse response = httpClient.request(method, "v1/" + path + queryString(params), body, headers);

        if (response.getStatus() >= 400) {
            throw FacturaSDKException.fromResponse(response.getStatus(), response.getData());
        }

        String data = response.getData();
        if (data == null || data.isEmpty()) {
            return null;
        }
        return GSON.fromJson(data, MAP_TYPE);
    }

    private static String queryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            query.append(query.length() == 0 ? "?" : "&");
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue().toString()));
        }
        return query.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AfipException("UTF-8 is not supported", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AfipException("Interrupted while waiting.", e);
        }
    }
}
