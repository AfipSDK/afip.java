package com.afipsdk;

import com.afipsdk.exception.AfipException;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.service.RegisterInscriptionProof;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegisterInscriptionProofTest {

    private static final String TA_RESPONSE = "{\"token\":\"test-token\",\"sign\":\"test-sign\"}";

    private StubHttpClient stub;
    private RegisterInscriptionProof service;

    @BeforeEach
    void setUp() {
        stub = new StubHttpClient();
        AfipOptions options = new AfipOptions();
        options.setCuit("20123456789");
        options.setProduction(false);
        service = new Afip(options, stub).registerInscriptionProof();
    }

    @Test
    void getServerStatus_returnsStatus() {
        stub.addResponse("{\"return\":{\"AppServer\":\"OK\",\"DbServer\":\"OK\",\"AuthServer\":\"OK\"}}");

        Map<String, Object> status = service.getServerStatus();

        assertNotNull(status);
        assertEquals("OK", status.get("AppServer"));
        assertEquals("OK", status.get("DbServer"));
        assertEquals("OK", status.get("AuthServer"));
    }

    @Test
    void getTaxpayerDetails_returnsPersona() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"personaReturn\":{\"idPersona\":20123456789,\"nombre\":\"EMPRESA TEST\"}}");

        Map<String, Object> result = service.getTaxpayerDetails(20123456789L);

        assertNotNull(result);
        assertEquals("EMPRESA TEST", result.get("nombre"));
    }

    @Test
    void getTaxpayerDetails_returnsNull_whenNoExiste() {
        stub.addResponse(TA_RESPONSE);
        stub.addException(new AfipException("No existe persona con ese identificador"));

        Map<String, Object> result = service.getTaxpayerDetails(99999999999L);

        assertNull(result);
    }

    @Test
    void getTaxpayersDetails_returnsList() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"personaListReturn\":{\"persona\":"
                + "[{\"idPersona\":20123456789},{\"idPersona\":20456789012}]}}");

        List<Map<String, Object>> result = service.getTaxpayersDetails(
                new long[]{20123456789L, 20456789012L});

        assertNotNull(result);
        assertEquals(2, result.size());
    }
}
