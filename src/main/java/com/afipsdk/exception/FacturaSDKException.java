package com.afipsdk.exception;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Error de la API de Factura SDK.
 *
 * Trae el status HTTP y el cuerpo de la respuesta: {@code message}, los problemas por campo en
 * {@code data_errors} y las reglas que el comprobante no cumplió en {@code errors}
 * (https://afipsdk.com/docs/facturasdk/errors).
 */
public class FacturaSDKException extends AfipException {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final int status;
    private final Map<String, Object> data;
    private final Map<String, Object> dataErrors;
    private final List<Map<String, Object>> errors;

    public FacturaSDKException(String message) {
        this(message, 0, null);
    }

    @SuppressWarnings("unchecked")
    public FacturaSDKException(String message, int status, Map<String, Object> data) {
        super(message);
        this.status = status;
        this.data = data;

        // Una automatización fallida responde { status: "error", data: { message, data_errors, errors } }
        Map<String, Object> detail = detailOf(data);

        Object dataErrors = detail.get("data_errors");
        this.dataErrors = dataErrors instanceof Map ? (Map<String, Object>) dataErrors : null;

        Object errors = detail.get("errors");
        this.errors = errors instanceof List ? (List<Map<String, Object>>) errors : null;
    }

    /** Status HTTP de la respuesta, o 0 si el error no vino de una respuesta. */
    public int getStatus() { return status; }

    /** Cuerpo de la respuesta, o {@code null} si no era JSON. */
    public Map<String, Object> getData() { return data; }

    /** Problemas por campo, anidados como la solicitud, o {@code null}. */
    public Map<String, Object> getDataErrors() { return dataErrors; }

    /** Reglas que no se cumplieron, cada una con {@code code} y {@code message}, o {@code null}. */
    public List<Map<String, Object>> getErrors() { return errors; }

    /**
     * Arma la excepción a partir de una respuesta fallida de la API.
     *
     * @param status status HTTP
     * @param body   cuerpo de la respuesta
     */
    @SuppressWarnings("unchecked")
    public static FacturaSDKException fromResponse(int status, String body) {
        Map<String, Object> data = null;
        if (body != null && !body.isEmpty()) {
            try {
                data = GSON.fromJson(body, MAP_TYPE);
            } catch (JsonSyntaxException e) {
                data = null;
            }
        }

        Map<String, Object> detail = detailOf(data);
        List<String> parts = new ArrayList<String>();

        Object message = detail.get("message");
        if (message != null && !message.toString().isEmpty()) {
            parts.add(message.toString());
        }

        Object errors = detail.get("errors");
        if (errors instanceof List) {
            for (Object item : (List<Object>) errors) {
                if (item instanceof Map) {
                    Map<String, Object> error = (Map<String, Object>) item;
                    parts.add(error.get("code") + ": " + error.get("message"));
                }
            }
        }

        Object dataErrors = detail.get("data_errors");
        if (dataErrors instanceof Map) {
            parts.addAll(flattenDataErrors((Map<String, Object>) dataErrors, ""));
        }

        String text;
        if (!parts.isEmpty()) {
            text = join(parts);
        } else if (data == null && body != null && !body.isEmpty()) {
            text = body;
        } else {
            text = "Factura SDK respondió " + status;
        }

        return new FacturaSDKException(text, status, data);
    }

    /**
     * Dónde viven message, data_errors y errors en un cuerpo: en el cuerpo mismo, o en
     * {@code data} cuando el cuerpo es una automatización fallida.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> detailOf(Map<String, Object> data) {
        if (data == null) {
            return Collections.emptyMap();
        }
        if ("error".equals(data.get("status")) && data.get("data") instanceof Map) {
            return (Map<String, Object>) data.get("data");
        }
        return data;
    }

    /** { receiver: { vat_condition: "mensaje" } } -> ["receiver.vat_condition: mensaje"] */
    @SuppressWarnings("unchecked")
    private static List<String> flattenDataErrors(Map<String, Object> errors, String prefix) {
        List<String> lines = new ArrayList<String>();
        for (Map.Entry<String, Object> entry : errors.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                lines.addAll(flattenDataErrors((Map<String, Object>) entry.getValue(), path));
            } else {
                lines.add(path + ": " + entry.getValue());
            }
        }
        return lines;
    }

    private static String join(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(part);
        }
        return builder.toString();
    }
}
