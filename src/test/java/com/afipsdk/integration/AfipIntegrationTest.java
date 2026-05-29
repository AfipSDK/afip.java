package com.afipsdk.integration;

import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.model.AutomationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests de integración contra la API real de AfipSDK.
 *
 * Para ejecutarlos, definí las variables de entorno:
 *   AFIP_TOKEN    — access token de AfipSDK
 *   AFIP_CUIT     — CUIT del contribuyente (ej: "20123456789")
 *   AFIP_PASSWORD — contraseña de acceso a ARCA (requerida para tests de automatizaciones)
 *
 * Si las variables no están presentes, los tests se omiten automáticamente.
 */
class AfipIntegrationTest {

    private Afip afip;
    private String cuit;
    private String password;

    @BeforeEach
    void setUp() {
        String token = System.getenv("AFIP_TOKEN");
        cuit         = System.getenv("AFIP_CUIT");
        password     = System.getenv("AFIP_PASSWORD");

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
        List<Map<String, Object>> types = afip.electronicBilling().getVoucherTypes();

        assertNotNull(types);
        assertFalse(types.isEmpty());
        System.out.println("Tipos de comprobante (" + types.size() + "): " + types.get(0));
    }

    @Test
    void electronicBilling_getLastVoucher_salesPoint1_type6() {
        int last = afip.electronicBilling().getLastVoucher(1, 6);

        assertTrue(last >= 0);
        System.out.println("Último comprobante tipo 6, punto de venta 1: " + last);
    }

    @Test
    void registerScopeThirteen_getServerStatus() {
        Map<String, Object> status = afip.registerScopeThirteen().getServerStatus();

        assertNotNull(status);
        System.out.println("Padron A13 server status: " + status);
    }

    // -------------------------------------------------------------------------
    // Automatización: monotributo-info
    // -------------------------------------------------------------------------

    @Test
    void monotributoInfo_withWait_returnsCompleteWithData() {
        assumeTrue(password != null && !password.isEmpty(),
                "AFIP_PASSWORD no configurado — omitiendo tests de automatizaciones");

        AutomationResponse result = afip.createAutomation("monotributo-info", buildMonotributoParams());

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("complete", result.getStatus());

        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertNotNull(data.get("category"));
        assertNotNull(data.get("billed_amount"));
        assertNotNull(data.get("category_limit"));
        assertNotNull(data.get("next_due_date"));
        assertNotNull(data.get("next_due_amount"));

        System.out.println("Categoría:              " + data.get("category"));
        System.out.println("Monto facturado:        " + data.get("billed_amount"));
        System.out.println("Fecha actualiz. factur: " + data.get("billing_update_date"));
        System.out.println("Límite de categoría:    " + data.get("category_limit"));
        System.out.println("Próximo vencimiento:    " + data.get("next_due_date"));
        System.out.println("Monto próx. venc.:      " + data.get("next_due_amount"));
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> buildMonotributoParams() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("cuit",     cuit);
        params.put("username", cuit);
        params.put("password", password);
        return params;
    }
}
