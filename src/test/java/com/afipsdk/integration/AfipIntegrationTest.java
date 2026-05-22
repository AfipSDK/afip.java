package com.afipsdk.integration;

import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests de integración contra la API real de AfipSDK.
 *
 * Para ejecutarlos, definí las variables de entorno:
 *   AFIP_TOKEN  — access token de AfipSDK
 *   AFIP_CUIT   — CUIT del contribuyente (ej: "20123456789")
 *
 * Si las variables no están presentes, los tests se omiten automáticamente.
 */
class AfipIntegrationTest {

    private Afip afip;

    @BeforeEach
    void setUp() {
        String token = System.getenv("AFIP_TOKEN");
        String cuit  = System.getenv("AFIP_CUIT");

        assumeTrue(token != null && !token.isEmpty(),
                "AFIP_TOKEN no configurado — omitiendo tests de integración");
        assumeTrue(cuit != null && !cuit.isEmpty(),
                "AFIP_CUIT no configurado — omitiendo tests de integración");

        AfipOptions options = new AfipOptions();
        options.setAccessToken(token);
        options.setCuit(cuit);
        options.setProduction(false);

        afip = new Afip(options);
    }

    @Test
    void electronicBilling_getServerStatus() {
        Map<String, Object> status = afip.electronicBilling().getServerStatus();

        assertNotNull(status);
        System.out.println("WSFE server status: " + status);
    }

    @Test
    void electronicBilling_getVoucherTypes() {
        java.util.List<Map<String, Object>> types = afip.electronicBilling().getVoucherTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de comprobante (" + types.size() + "): " + types.get(0));
    }

    @Test
    void electronicBilling_getLastVoucher_salesPoint1_type6() {
        // Factura B — requiere al menos un punto de venta habilitado
        int last = afip.electronicBilling().getLastVoucher(1, 6);

        System.out.println("Último comprobante tipo 6, punto de venta 1: " + last);
        assertTrue(last >= 0);
    }

    @Test
    void registerScopeThirteen_getServerStatus() {
        Map<String, Object> status = afip.registerScopeThirteen().getServerStatus();

        assertNotNull(status);
        System.out.println("Padron A13 server status: " + status);
    }
}
