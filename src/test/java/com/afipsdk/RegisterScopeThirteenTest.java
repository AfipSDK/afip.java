package com.afipsdk;

import com.afipsdk.exception.AfipException;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.service.RegisterScopeThirteen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegisterScopeThirteenTest {

    private static final String TA_RESPONSE = "{\"token\":\"test-token\",\"sign\":\"test-sign\"}";

    private StubHttpClient stub;
    private RegisterScopeThirteen service;

    @BeforeEach
    void setUp() {
        stub = new StubHttpClient();
        AfipOptions options = new AfipOptions();
        options.setCuit("20123456789");
        options.setProduction(false);
        service = new Afip(options, stub).registerScopeThirteen();
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
        stub.addResponse("{\"personaReturn\":{\"persona\":{\"idPersona\":20123456789,\"tipoPersona\":\"FISICA\"}}}");

        Map<String, Object> result = service.getTaxpayerDetails(20123456789L);

        assertNotNull(result);
        assertEquals("FISICA", result.get("tipoPersona"));
    }

    @Test
    void getTaxpayerDetails_returnsNull_whenNoExiste() {
        stub.addResponse(TA_RESPONSE);
        stub.addException(new AfipException("No existe persona con ese identificador"));

        Map<String, Object> result = service.getTaxpayerDetails(99999999999L);

        assertNull(result);
    }

    @Test
    void getTaxIDByDocument_returnsId() {
        stub.addResponse(TA_RESPONSE);
        stub.addResponse("{\"idPersonaListReturn\":{\"idPersona\":20123456789}}");

        Object result = service.getTaxIDByDocument(12345678L);

        assertNotNull(result);
    }

    @Test
    void getTaxIDByDocument_returnsNull_whenNoExiste() {
        stub.addResponse(TA_RESPONSE);
        stub.addException(new AfipException("No existe persona con ese documento"));

        Object result = service.getTaxIDByDocument(99999999L);

        assertNull(result);
    }
}
