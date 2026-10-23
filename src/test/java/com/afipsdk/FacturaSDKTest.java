package com.afipsdk;

import com.afipsdk.exception.FacturaSDKException;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.model.AutomationResponse;
import com.afipsdk.service.FacturaSDK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FacturaSDKTest {

    private static final String QUEUED = "{\"id\":\"inv-1\",\"idempotency_key\":\"venta-0001\",\"status\":\"queued\",\"number\":null}";
    private static final String APPROVED = "{\"id\":\"inv-1\",\"status\":\"approved\",\"number\":482,\"cae\":\"76412345678901\","
            + "\"pdf_url\":\"https://app.afipsdk.com/api/v1/invoices/inv-1/pdf?token=t\"}";

    private StubHttpClient stub;
    private FacturaSDK facturaSDK;

    @BeforeEach
    void setUp() {
        stub = new StubHttpClient();
        AfipOptions options = new AfipOptions();
        options.setAccessToken("fsk_dev_test");
        facturaSDK = new Afip(options, stub).facturaSDK();
    }

    @Test
    void createInvoice_postsWithIdempotencyKey_andReturnsQueued() {
        stub.addResponse(202, QUEUED);

        Map<String, Object> data = invoiceData();
        Map<String, Object> invoice = facturaSDK.createInvoice(data, "venta-0001");

        assertEquals("queued", invoice.get("status"));
        StubHttpClient.Call call = stub.lastCall();
        assertEquals("POST", call.method);
        assertEquals("v1/invoices", call.path);
        assertEquals("venta-0001", call.headers.get("Idempotency-Key"));
        assertEquals("20111111112", call.body.get("issuer_cuit"));
    }

    @Test
    void createInvoice_withoutKey_sendsNoHeader() {
        stub.addResponse(202, QUEUED);

        facturaSDK.createInvoice(invoiceData());

        assertNull(stub.lastCall().headers);
    }

    @Test
    void createInvoice_withWait_pollsUntilDecided() {
        stub.addResponse(202, QUEUED);
        stub.addResponse("{\"id\":\"inv-1\",\"status\":\"processing\"}");
        stub.addResponse(APPROVED);

        Map<String, Object> invoice = facturaSDK.createInvoice(invoiceData(), "venta-0001", true);

        assertEquals("approved", invoice.get("status"));
        assertEquals(482.0, invoice.get("number"));
        assertEquals(3, stub.getCalls().size());
        assertEquals("v1/invoices/inv-1", stub.lastCall().path);
    }

    @Test
    void createInvoice_withWait_returnsFinalWithoutPolling() {
        stub.addResponse(200, APPROVED);

        facturaSDK.createInvoice(invoiceData(), "venta-0001", true);

        assertEquals(1, stub.getCalls().size());
    }

    @Test
    void getInvoice_returnsInvoice_andWaitPolls() {
        stub.addResponse(APPROVED);
        assertEquals("approved", facturaSDK.getInvoice("inv-1").get("status"));
        assertEquals("v1/invoices/inv-1", stub.lastCall().path);
    }

    @Test
    void formError_throwsFacturaSDKException_withDataErrors() {
        stub.addResponse(400, "{\"statusCode\":400,\"data_errors\":{\"receiver\":{\"vat_condition\":\"El campo es invalido\"}}}");

        FacturaSDKException ex = assertThrows(FacturaSDKException.class, () -> facturaSDK.createInvoice(invoiceData()));

        assertEquals(400, ex.getStatus());
        assertEquals("receiver.vat_condition: El campo es invalido", ex.getMessage());
        assertEquals("El campo es invalido", ((Map<?, ?>) ex.getDataErrors().get("receiver")).get("vat_condition"));
        assertNull(ex.getErrors());
    }

    @Test
    void ruleError_throwsFacturaSDKException_withErrors() {
        stub.addResponse(400, "{\"message\":\"El comprobante no cumple las validaciones.\","
                + "\"errors\":[{\"code\":\"amounts_mismatch\",\"message\":\"El total debe ser igual a la suma\"}]}");

        FacturaSDKException ex = assertThrows(FacturaSDKException.class, () -> facturaSDK.createInvoice(invoiceData()));

        assertEquals("El comprobante no cumple las validaciones. amounts_mismatch: El total debe ser igual a la suma", ex.getMessage());
        assertEquals("amounts_mismatch", ex.getErrors().get(0).get("code"));
    }

    @Test
    void nonJsonError_usesBodyAsMessage() {
        stub.addResponse(502, "<html>Bad Gateway</html>");

        FacturaSDKException ex = assertThrows(FacturaSDKException.class, () -> facturaSDK.getInvoice("inv-1"));

        assertEquals(502, ex.getStatus());
        assertEquals("<html>Bad Gateway</html>", ex.getMessage());
        assertNull(ex.getData());
    }

    @Test
    void emptyError_usesStatusAsMessage() {
        stub.addResponse(500, "");

        FacturaSDKException ex = assertThrows(FacturaSDKException.class, () -> facturaSDK.getInvoice("inv-1"));

        assertEquals("Factura SDK respondió 500", ex.getMessage());
    }

    @Test
    void getInvoices_sendsFiltersAsQuery() {
        stub.addResponse("{\"data\":[],\"next_cursor\":null,\"total_count\":0}");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("status", "approved");
        params.put("limit", 5);
        Map<String, Object> page = facturaSDK.getInvoices(params);

        assertEquals(0.0, page.get("total_count"));
        String path = stub.lastCall().path;
        assertTrue(path.startsWith("v1/invoices?"), path);
        assertTrue(path.contains("status=approved"), path);
        assertTrue(path.contains("limit=5"), path);
    }

    @Test
    void cancelInvoice_postsCancel() {
        stub.addResponse("{\"id\":\"inv-1\",\"status\":\"cancelled\"}");

        assertEquals("cancelled", facturaSDK.cancelInvoice("inv-1").get("status"));
        assertEquals("v1/invoices/inv-1/cancel", stub.lastCall().path);
    }

    @Test
    void issuers_crud() {
        stub.addResponse(201, "{\"id\":\"iss-1\",\"tax_id\":\"20111111112\",\"status\":\"pending\"}");
        stub.addResponse("{\"id\":\"iss-1\",\"address\":\"Otra 1\"}");
        stub.addResponse(204, "");
        stub.addResponse("{\"tax_id\":\"20111111112\",\"issuer\":{\"business_name\":\"PEREZ JUAN\"}}");

        Map<String, Object> issuer = new HashMap<String, Object>();
        issuer.put("tax_id", "20111111112");
        assertEquals("pending", facturaSDK.createIssuer(issuer).get("status"));

        Map<String, Object> change = new HashMap<String, Object>();
        change.put("address", "Otra 1");
        assertEquals("Otra 1", facturaSDK.updateIssuer("iss-1", change).get("address"));
        assertEquals("PUT", stub.lastCall().method);

        facturaSDK.deleteIssuer("iss-1");
        assertEquals("DELETE", stub.lastCall().method);
        assertEquals("v1/issuers/iss-1", stub.lastCall().path);

        Map<String, Object> lookup = facturaSDK.lookupIssuer("20111111112");
        assertEquals("PEREZ JUAN", ((Map<?, ?>) lookup.get("issuer")).get("business_name"));
        assertEquals("v1/issuers/lookup?tax_id=20111111112", stub.lastCall().path);
    }

    @Test
    void automation_withWait_pollsUntilComplete() {
        stub.addResponse(202, "{\"status\":\"in_process\",\"automation_id\":\"auto-1\",\"message\":\"En proceso\"}");
        stub.addResponse("{\"id\":\"auto-1\",\"status\":\"in_process\"}");
        stub.addResponse("{\"id\":\"auto-1\",\"status\":\"complete\",\"data\":{\"verified\":true}}");

        AutomationResponse automation = facturaSDK.verifyIssuer("iss-1");

        assertEquals("complete", automation.getStatus());
        assertEquals(true, automation.getData().get("verified"));
        assertEquals("v1/automations/auto-1", stub.lastCall().path);
    }

    @Test
    void automation_withoutWait_returnsStartedAutomation() {
        stub.addResponse(202, "{\"status\":\"in_process\",\"automation_id\":\"auto-1\",\"message\":\"En proceso\"}");

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("tax_id", "20111111112");
        AutomationResponse automation = facturaSDK.getSalesPoints(data, false);

        assertEquals("auto-1", automation.getId());
        assertEquals("in_process", automation.getStatus());
    }

    @Test
    void automationError_throwsFacturaSDKException_withInnerMessage() {
        stub.addResponse(202, "{\"status\":\"in_process\",\"automation_id\":\"auto-1\"}");
        stub.addResponse(400, "{\"id\":\"auto-1\",\"status\":\"error\",\"data\":{\"statusCode\":400,"
                + "\"message\":\"Clave fiscal incorrecta.\",\"data_errors\":{\"password\":\"Clave fiscal incorrecta\"}}}");

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("password", "INVALID_PASSWORD");
        FacturaSDKException ex = assertThrows(FacturaSDKException.class, () -> facturaSDK.getSalesPoints(data));

        assertEquals(400, ex.getStatus());
        assertEquals("Clave fiscal incorrecta. password: Clave fiscal incorrecta", ex.getMessage());
        assertEquals("error", ex.getData().get("status"));
        assertEquals("Clave fiscal incorrecta", ex.getDataErrors().get("password"));
    }

    @Test
    void createCertificate_manual_returnsCertificate_andAutomation_returnsAutomationMap() {
        stub.addResponse(201, "{\"id\":\"cert-1\",\"status\":\"active\",\"source\":\"manual\"}");
        stub.addResponse(202, "{\"status\":\"in_process\",\"automation_id\":\"auto-2\"}");
        stub.addResponse("{\"id\":\"auto-2\",\"status\":\"complete\",\"data\":{\"certificate\":{\"id\":\"cert-2\"},\"created\":true}}");

        Map<String, Object> manual = new HashMap<String, Object>();
        manual.put("cert", "VALID_CERT");
        manual.put("key", "VALID_KEY");
        assertEquals("manual", facturaSDK.createCertificate(manual).get("source"));

        Map<String, Object> automatic = new HashMap<String, Object>();
        automatic.put("source", "automation");
        Map<String, Object> automation = facturaSDK.createIssuerCertificate("iss-1", automatic);
        assertEquals("complete", automation.get("status"));
        assertEquals("cert-2", ((Map<?, ?>) ((Map<?, ?>) automation.get("data")).get("certificate")).get("id"));
    }

    @Test
    void webhookSignature_verifiesAndConstructsEvent() throws Exception {
        String secret = "whsec_test";
        String body = "{\"event_id\":\"evt_1\",\"type\":\"invoice.approved\",\"data\":{\"id\":\"inv-1\"}}";
        long now = System.currentTimeMillis() / 1000;
        String header = sign(body, secret, now);

        assertTrue(facturaSDK.verifyWebhookSignature(header, body, secret));
        assertTrue(facturaSDK.verifyWebhookSignature(header, body.getBytes(StandardCharsets.UTF_8), secret, 300));
        assertFalse(facturaSDK.verifyWebhookSignature(header, body + " ", secret));
        assertFalse(facturaSDK.verifyWebhookSignature(header, body, "whsec_otro"));
        assertFalse(facturaSDK.verifyWebhookSignature(sign(body, secret, now - 600), body, secret));
        assertFalse(facturaSDK.verifyWebhookSignature(null, body, secret));
        assertFalse(facturaSDK.verifyWebhookSignature("t=abc,v1=", body, secret));
        assertFalse(facturaSDK.verifyWebhookSignature("garbage", body, secret));

        Map<String, Object> event = facturaSDK.constructWebhookEvent(body, header, secret);
        assertEquals("invoice.approved", event.get("type"));

        FacturaSDKException ex = assertThrows(FacturaSDKException.class,
                () -> facturaSDK.constructWebhookEvent(body, "t=1,v1=00", secret));
        assertEquals("La firma del webhook no es válida", ex.getMessage());
        assertEquals(0, ex.getStatus());
    }

    @Test
    void afip_withFacturaSdkToken_buildsService() {
        AfipOptions options = new AfipOptions();
        options.setCuit("20111111112");
        options.setAccessToken("fsk_prod_abc");

        Afip afip = new Afip(options);

        assertNotNull(afip.facturaSDK());
        assertEquals("fsk_prod_abc", afip.getOptions().getAccessToken());
    }

    // -------------------------------------------------------------------------

    private static Map<String, Object> invoiceData() {
        Map<String, Object> receiver = new HashMap<String, Object>();
        receiver.put("doc_type", 96);
        receiver.put("doc_number", "12345678");
        receiver.put("vat_condition", 5);
        receiver.put("name", "Juan Pérez");
        receiver.put("address", "Calle Falsa 123");

        Map<String, Object> amounts = new HashMap<String, Object>();
        amounts.put("net", 1000);
        amounts.put("vat", 210);
        amounts.put("total", 1210);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("issuer_cuit", "20111111112");
        data.put("invoice_type", 6);
        data.put("concept", 1);
        data.put("receiver", receiver);
        data.put("amounts", amounts);
        data.put("sale_condition", "Contado");
        return data;
    }

    private static String sign(String body, String secret, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        return "t=" + timestamp + ",v1=" + hex;
    }
}
