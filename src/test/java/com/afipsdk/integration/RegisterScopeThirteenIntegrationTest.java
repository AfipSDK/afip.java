package com.afipsdk.integration;

import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests de integración para RegisterScopeThirteen contra la API real de AfipSDK (sandbox).
 *
 * Requiere variables de entorno: AFIP_TOKEN, AFIP_CUIT
 */
class RegisterScopeThirteenIntegrationTest {

    private Afip afip;
    private long cuit;
    private long exampleCuit;

    @BeforeEach
    void setUp() {
        String token   = System.getenv("AFIP_TOKEN");
        String cuitStr = System.getenv("AFIP_CUIT");

        assumeTrue(token   != null && !token.isEmpty(),   "AFIP_TOKEN no configurado — omitiendo tests de integración");
        assumeTrue(cuitStr != null && !cuitStr.isEmpty(), "AFIP_CUIT no configurado — omitiendo tests de integración");

        cuit = Long.parseLong(cuitStr);

        AfipOptions options = new AfipOptions();
        options.setAccessToken(token);
        options.setCuit(cuitStr);
        options.setProduction(false);

        afip = new Afip(options);
        exampleCuit = Long.parseLong("33693450239");
    }

    @Test
    void getServerStatus() {
        Map<String, Object> status = afip.registerScopeThirteen().getServerStatus();

        assertNotNull(status);
        System.out.println("Padrón A13 server status: " + status);
    }

    @Test
    void getTaxpayerDetails_withValidCuit() {

        Map<String, Object> details = afip.registerScopeThirteen().getTaxpayerDetails(exampleCuit);

        assertNotNull(details);
        System.out.println("Datos contribuyente A13: " + details);
    }

    @Test
    void getTaxpayerDetails_withInvalidCuit_returnsNull() {
        Map<String, Object> details = afip.registerScopeThirteen().getTaxpayerDetails(20379908676L);

        assertNull(details);
    }

    @Test
    void getTaxIDByDocument_withValidDocument() {
        // Extrae los 8 dígitos del documento del CUIT (formato: XX_XXXXXXXX_X)
        long docNumber = (cuit / 10) % 100_000_000L;

        Object result = afip.registerScopeThirteen().getTaxIDByDocument(docNumber);

        // Puede devolver un número o una lista; solo verificamos que no lance excepción
        System.out.println("ID por documento " + docNumber + ": " + result);
    }
}
