package com.afipsdk;

import com.afipsdk.exception.AfipWebServiceException;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.service.ElectronicBilling;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElectronicBillingTest {

    private static final String TA_RESPONSE = "{\"token\":\"test-token\",\"sign\":\"test-sign\"}";

    private StubHttpClient stub;
    private ElectronicBilling billing;

    @BeforeEach
    void setUp() {
        stub = new StubHttpClient();
        AfipOptions options = new AfipOptions();
        options.setCuit("20123456789");
        options.setProduction(false);
        billing = new Afip(options, stub).electronicBilling();
    }

    @Test
    void getLastVoucher_returnsCorrectNumber() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECompUltimoAutorizadoResult\":{\"PtoVta\":1,\"CbteNro\":42,\"CbteTipo\":6}}");

        int result = billing.getLastVoucher(1, 6);

        assertEquals(42, result);
    }

    @Test
    void getServerStatus_returnsStatus() {
        stub.addResponse("{\"FEDummyResult\":{\"AppServer\":\"OK\",\"DbServer\":\"OK\",\"AuthServer\":\"OK\"}}");

        Map<String, Object> status = billing.getServerStatus();

        assertEquals("OK", status.get("AppServer"));
        assertEquals("OK", status.get("DbServer"));
        assertEquals("OK", status.get("AuthServer"));
    }

    @Test
    void createVoucher_returnsCAEAndFormattedDate() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECAESolicitarResult\":{"
                + "\"FeCabResp\":{\"CantReg\":1,\"PtoVta\":1,\"CbteTipo\":6,\"Resultado\":\"A\"},"
                + "\"FeDetResp\":{\"FECAEDetResponse\":{"
                + "\"CAE\":\"12345678901234\",\"CAEFchVto\":\"20241231\","
                + "\"Resultado\":\"A\",\"CbteDesde\":1,\"CbteHasta\":1"
                + "}}}}");

        Map<String, Object> result = billing.createVoucher(minimalVoucherData());

        assertEquals("12345678901234", result.get("CAE"));
        assertEquals("2024-12-31", result.get("CAEFchVto"));
    }

    @Test
    void createVoucher_throwsAfipWebServiceException_onAfipError() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECAESolicitarResult\":{\"Errors\":{\"Err\":{\"Code\":600,\"Msg\":\"Error de prueba\"}}}}");

        AfipWebServiceException ex = assertThrows(AfipWebServiceException.class,
                () -> billing.createVoucher(minimalVoucherData()));

        assertEquals(600, ex.getCode());
        assertTrue(ex.getMessage().contains("Error de prueba"));
    }

    @Test
    void getVoucherInfo_returnsNull_whenCode602() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECompConsultarResult\":{\"Errors\":{\"Err\":{\"Code\":602,\"Msg\":\"El comprobante no existe\"}}}}");

        Map<String, Object> result = billing.getVoucherInfo(9999, 1, 6);

        assertNull(result);
    }

    @Test
    void createNextVoucher_incrementsLastVoucher() {
        // getLastVoucher: devuelve 5
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECompUltimoAutorizadoResult\":{\"CbteNro\":5}}");
        // createVoucher con número 6
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECAESolicitarResult\":{"
                + "\"FeDetResp\":{\"FECAEDetResponse\":{"
                + "\"CAE\":\"99999999999999\",\"CAEFchVto\":\"20241231\","
                + "\"Resultado\":\"A\"}}}}");

        Map<String, Object> data = minimalVoucherData();
        Map<String, Object> result = billing.createNextVoucher(data);

        assertEquals(6, result.get("voucherNumber"));
        assertEquals("99999999999999", result.get("CAE"));
    }

    @Test
    void getSalesPoints_returnsList() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FEParamGetPtosVentaResult\":{\"ResultGet\":{\"PtoVenta\":"
                + "[{\"Nro\":1,\"EmisionTipo\":\"CAE\"},{\"Nro\":2,\"EmisionTipo\":\"CAE\"}]}}}");

        List<Map<String, Object>> points = billing.getSalesPoints();

        assertNotNull(points);
        assertEquals(2, points.size());
    }

    @Test
    void createVoucher_withIva_wrapsAliquotaAndReturnsCAE() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECAESolicitarResult\":{"
                + "\"FeCabResp\":{\"CantReg\":1,\"PtoVta\":1,\"CbteTipo\":6,\"Resultado\":\"A\"},"
                + "\"FeDetResp\":{\"FECAEDetResponse\":{"
                + "\"CAE\":\"12345678901234\",\"CAEFchVto\":\"20241231\","
                + "\"Resultado\":\"A\",\"CbteDesde\":43,\"CbteHasta\":43"
                + "}}}}");

        Map<String, Object> result = billing.createVoucher(sandboxVoucherData(43));

        assertEquals("12345678901234", result.get("CAE"));
        assertEquals("2024-12-31", result.get("CAEFchVto"));
    }

    @Test
    void createVoucher_fullFlow_getLastThenCreate() {
        // getLastVoucher devuelve 42 → el siguiente es 43
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECompUltimoAutorizadoResult\":{\"PtoVta\":1,\"CbteNro\":42,\"CbteTipo\":6}}");
        // createVoucher con número 43
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"FECAESolicitarResult\":{"
                + "\"FeDetResp\":{\"FECAEDetResponse\":{"
                + "\"CAE\":\"12345678901234\",\"CAEFchVto\":\"20241231\","
                + "\"Resultado\":\"A\",\"CbteDesde\":43,\"CbteHasta\":43"
                + "}}}}");

        int lastVoucher     = billing.getLastVoucher(1, 6);
        int numeroDeFactura = lastVoucher + 1;

        assertEquals(43, numeroDeFactura);

        Map<String, Object> result = billing.createVoucher(sandboxVoucherData(numeroDeFactura));

        assertEquals("12345678901234", result.get("CAE"));
        assertEquals("2024-12-31", result.get("CAEFchVto"));
    }

    // -------------------------------------------------------------------------

    private static Map<String, Object> minimalVoucherData() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("CbteDesde", 1);
        data.put("CbteHasta", 1);
        data.put("PtoVta", 1);
        data.put("CbteTipo", 6);
        data.put("Concepto", 1);
        data.put("DocTipo", 99);
        data.put("DocNro", 0);
        data.put("CbteFch", "20241201");
        data.put("ImpTotal", 121.0);
        data.put("ImpTotConc", 0);
        data.put("ImpNeto", 100.0);
        data.put("ImpOpEx", 0);
        data.put("ImpIVA", 21.0);
        data.put("ImpTrib", 0);
        data.put("MonId", "PES");
        data.put("MonCotiz", 1);
        return data;
    }

    /** Replica el data del ejemplo Sandbox.java: Factura B con IVA 21% y consumidor final. */
    private static Map<String, Object> sandboxVoucherData(int numeroDeFactura) {
        Map<String, Object> alicuota = new HashMap<String, Object>();
        alicuota.put("Id",      5);     // 5 = 21%
        alicuota.put("BaseImp", 100.0);
        alicuota.put("Importe", 21.0);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("CantReg",   1);
        data.put("PtoVta",    1);
        data.put("CbteTipo",  6);       // 6 = Factura B
        data.put("Concepto",  1);       // 1 = Productos
        data.put("DocTipo",   99);      // 99 = Consumidor Final
        data.put("DocNro",    0);
        data.put("CbteDesde", numeroDeFactura);
        data.put("CbteHasta", numeroDeFactura);
        data.put("CbteFch",   20241201);
        data.put("ImpTotal",  121.0);
        data.put("ImpTotConc", 0);
        data.put("ImpNeto",   100.0);
        data.put("ImpOpEx",   0.0);
        data.put("ImpIVA",    21.0);
        data.put("ImpTrib",   0);
        data.put("MonId",     "PES");
        data.put("MonCotiz",  1);
        data.put("CondicionIVAReceptorId", 5);  // 5 = Consumidor Final
        data.put("Iva", Arrays.asList(alicuota));
        return data;
    }
}
