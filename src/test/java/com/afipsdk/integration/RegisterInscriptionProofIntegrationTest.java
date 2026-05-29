package com.afipsdk.integration;

import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests de integración para RegisterInscriptionProof contra la API real de AfipSDK (sandbox).
 *
 * Requiere variables de entorno: AFIP_TOKEN, AFIP_CUIT
 */
class RegisterInscriptionProofIntegrationTest {

    private Afip afip;
    private long cuit;

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
    }

    @Test
    void getServerStatus() {
        Map<String, Object> status = afip.registerInscriptionProof().getServerStatus();

        assertNotNull(status);
        System.out.println("Constancia inscripción server status: " + status);
    }

    @Test
    void getTaxpayerDetails_withValidCuit() {
        Map<String, Object> details = afip.registerInscriptionProof().getTaxpayerDetails(cuit);

        // El sandbox de ws_sr_constancia_inscripcion no siempre tiene el CUIT cargado
        assumeTrue(details != null, "CUIT no registrado en el sandbox de ws_sr_constancia_inscripcion — omitiendo");
        System.out.println("Datos contribuyente (constancia): " + details);
    }

    @Test
    void getTaxpayerDetails_withInvalidCuit_returnsNull() {
        Map<String, Object> details = afip.registerInscriptionProof().getTaxpayerDetails(99999999999L);

        assertNull(details);
    }

    @Test
    void getTaxpayersDetails_withMultipleCuits() {
        List<Map<String, Object>> details = afip.registerInscriptionProof().getTaxpayersDetails(new long[]{cuit});

        assertNotNull(details);
        assertFalse(details.isEmpty());
        System.out.println("Datos múltiples contribuyentes (" + details.size() + "): " + details.get(0));
    }
}
