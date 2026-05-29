package com.afipsdk.integration;

import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests de integración para ElectronicBilling contra la API real de AfipSDK (sandbox).
 *
 * Requiere variables de entorno: AFIP_TOKEN, AFIP_CUIT
 */
class ElectronicBillingIntegrationTest {

    private static final int SALES_POINT  = 1;
    private static final int VOUCHER_TYPE = 6; // Factura B

    private Afip afip;

    @BeforeEach
    void setUp() {
        String token   = System.getenv("AFIP_TOKEN");
        String cuit    = System.getenv("AFIP_CUIT");

        assumeTrue(token != null && !token.isEmpty(), "AFIP_TOKEN no configurado — omitiendo tests de integración");
        assumeTrue(cuit  != null && !cuit.isEmpty(),  "AFIP_CUIT no configurado — omitiendo tests de integración");

        AfipOptions options = new AfipOptions();
        options.setAccessToken(token);
        options.setCuit(cuit);
        options.setProduction(false);

        afip = new Afip(options);
    }

    @Test
    void getServerStatus() {
        Map<String, Object> status = afip.electronicBilling().getServerStatus();

        assertNotNull(status);
        System.out.println("WSFE server status: " + status);
    }

    @Test
    void getLastVoucher() {
        int last = afip.electronicBilling().getLastVoucher(SALES_POINT, VOUCHER_TYPE);

        assertTrue(last >= 0);
        System.out.println("Último comprobante tipo " + VOUCHER_TYPE + ", PV " + SALES_POINT + ": " + last);
    }

    @Test
    void getVoucherTypes() {
        List<Map<String, Object>> types = afip.electronicBilling().getVoucherTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de comprobante (" + types.size() + "): " + types.get(0));
    }

    @Test
    void getConceptTypes() {
        List<Map<String, Object>> types = afip.electronicBilling().getConceptTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de concepto (" + types.size() + "): " + types.get(0));
    }

    @Test
    void getDocumentTypes() {
        List<Map<String, Object>> types = afip.electronicBilling().getDocumentTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de documento (" + types.size() + "): " + types.get(0));
    }

    @Test
    void getAliquotTypes() {
        List<Map<String, Object>> types = afip.electronicBilling().getAliquotTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Alícuotas IVA (" + types.size() + "): " + types.get(0));
    }

    @Test
    void getCurrenciesTypes() {
        List<Map<String, Object>> types = afip.electronicBilling().getCurrenciesTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de moneda (" + types.size() + "): " + types.get(0));
    }

    @Test
    void getOptionsTypes() {
        // Puede ser null si el contribuyente no tiene opcionales habilitados
        List<Map<String, Object>> types = afip.electronicBilling().getOptionsTypes();

        System.out.println("Tipos opcionales: " + (types != null ? types.size() : "null"));
    }

    @Test
    void getTaxTypes() {
        List<Map<String, Object>> types = afip.electronicBilling().getTaxTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de tributo (" + types.size() + "): " + types.get(0));
    }

    @Test
    void getVoucherInfo_returnsNull_forNonExistentVoucher() {
        Map<String, Object> info = afip.electronicBilling().getVoucherInfo(999999, SALES_POINT, VOUCHER_TYPE);

        assertNull(info);
    }

    @Test
    void createNextVoucher() {
        Map<String, Object> data = buildVoucherData();

        Map<String, Object> result = afip.electronicBilling().createNextVoucher(data);

        assertNotNull(result);
        assertNotNull(result.get("CAE"));
        assertNotNull(result.get("CAEFchVto"));
        assertNotNull(result.get("voucherNumber"));
        System.out.println("Comprobante creado: " + result);
    }

    @Test
    void createVoucher_fullFlow_getLastThenCreate() {
        int lastVoucher     = afip.electronicBilling().getLastVoucher(SALES_POINT, VOUCHER_TYPE);
        int numeroDeFactura = lastVoucher + 1;

        Map<String, Object> data = buildVoucherDataWithIva(numeroDeFactura);

        Map<String, Object> result = afip.electronicBilling().createVoucher(data);

        assertNotNull(result);
        assertNotNull(result.get("CAE"));
        assertNotNull(result.get("CAEFchVto"));
        System.out.println("CAE:         " + result.get("CAE"));
        System.out.println("Vencimiento: " + result.get("CAEFchVto"));
    }

    private static Map<String, Object> buildVoucherData() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("PtoVta",     SALES_POINT);
        data.put("CbteTipo",   VOUCHER_TYPE);
        data.put("Concepto",   1);
        data.put("DocTipo",    99);
        data.put("DocNro",     0);
        data.put("CbteFch",    today);
        data.put("ImpTotal",   121.0);
        data.put("ImpTotConc", 0);
        data.put("ImpNeto",    100.0);
        data.put("ImpOpEx",    0);
        data.put("ImpIVA",     21.0);
        data.put("ImpTrib",    0);
        data.put("MonId",      "PES");
        data.put("MonCotiz",   1);
        data.put("CondicionIVAReceptorId", 5);  // 5 = Consumidor Final

        Map<String, Object> alicuota = new HashMap<String, Object>();
        alicuota.put("Id",      5);     // 5 = 21%
        alicuota.put("BaseImp", 100.0);
        alicuota.put("Importe", 21.0);
        data.put("Iva", Arrays.asList(alicuota));

        return data;
    }

    /** Replica el data del ejemplo Sandbox.java: Factura B con IVA 21% y consumidor final. */
    private static Map<String, Object> buildVoucherDataWithIva(int numeroDeFactura) {
        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        Map<String, Object> alicuota = new HashMap<String, Object>();
        alicuota.put("Id",      5);     // 5 = 21%
        alicuota.put("BaseImp", 100.0);
        alicuota.put("Importe", 21.0);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("CantReg",   1);
        data.put("PtoVta",    SALES_POINT);
        data.put("CbteTipo",  VOUCHER_TYPE);
        data.put("Concepto",  1);       // 1 = Productos
        data.put("DocTipo",   99);      // 99 = Consumidor Final
        data.put("DocNro",    0);
        data.put("CbteDesde", numeroDeFactura);
        data.put("CbteHasta", numeroDeFactura);
        data.put("CbteFch",   today);
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
