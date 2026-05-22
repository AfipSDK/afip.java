package com.afipsdk.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Respuesta de una operación de automatización de AfipSDK.
 */
public final class AutomationResponse {

    private String id;
    private String status;
    private Map<String, Object> data = new HashMap<String, Object>();

    /** Identificador de la automatización. */
    public String getId() { return id; }

    /** Estado de la automatización. */
    public String getStatus() { return status; }

    /** Datos devueltos por la automatización. */
    public Map<String, Object> getData() { return data; }
}
