package com.afipsdk;

import com.afipsdk.exception.AfipException;
import com.afipsdk.model.AfipOptions;
import com.afipsdk.model.AutomationResponse;
import com.afipsdk.model.GetLastRequestXmlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AfipTest {
    // Ejecutar los tests unitarios mvn test "-Dtest=!**/integration/*"

    private StubHttpClient stub;
    private Afip afip;

    @BeforeEach
    void setUp() {
        stub = new StubHttpClient();
        AfipOptions options = new AfipOptions();
        options.setCuit("20123456789");
        options.setProduction(false);
        afip = new Afip(options, stub);
    }

    @Test
    void getLastRequestXml_returnsResponse() {
        stub.addResponse("{\"request\":\"<xml>req</xml>\",\"response\":\"<xml>resp</xml>\"}");

        GetLastRequestXmlResponse result = afip.getLastRequestXml();

        assertNotNull(result);
        assertEquals("<xml>req</xml>", result.getRequest());
        assertEquals("<xml>resp</xml>", result.getResponse());
    }

    @Test
    void createAutomation_returnsCompleted_whenAlreadyComplete() {
        stub.addResponse("{\"id\":\"abc123\",\"status\":\"complete\",\"data\":{}}");

        AutomationResponse result = afip.createAutomation("test-automation", null);

        assertNotNull(result);
        assertEquals("abc123", result.getId());
        assertEquals("complete", result.getStatus());
    }

    @Test
    void createAutomation_pollsUntilComplete() {
        stub.addResponse("{\"id\":\"abc123\",\"status\":\"pending\",\"data\":{}}");
        stub.addResponse("{\"id\":\"abc123\",\"status\":\"complete\",\"data\":{\"result\":\"ok\"}}");

        AutomationResponse result = afip.createAutomation("test-automation", null, true);

        assertNotNull(result);
        assertEquals("complete", result.getStatus());
    }

    @Test
    void getAutomationDetails_returnsDetails() {
        stub.addResponse("{\"id\":\"abc123\",\"status\":\"complete\",\"data\":{}}");

        AutomationResponse result = afip.getAutomationDetails("abc123");

        assertNotNull(result);
        assertEquals("abc123", result.getId());
        assertEquals("complete", result.getStatus());
    }

    @Test
    void getAutomationDetails_throwsException_whenIdIsEmpty() {
        assertThrows(AfipException.class, () -> afip.getAutomationDetails(""));
    }
}
