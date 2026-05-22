package com.afipsdk.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Parámetros para crear un PDF de comprobante electrónico.
 */
public final class CreatePDFRequest {

    private String fileName = "";
    private String sendTo;
    private Map<String, Object> template = new HashMap<String, Object>();

    public CreatePDFRequest() {}

    /** Nombre del archivo PDF a generar (sin extensión). */
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    /** Dirección de email a la que enviar el PDF generado (opcional). */
    public String getSendTo() { return sendTo; }
    public void setSendTo(String sendTo) { this.sendTo = sendTo; }

    /** Datos del template para el PDF. */
    public Map<String, Object> getTemplate() { return template; }
    public void setTemplate(Map<String, Object> template) { this.template = template; }
}
