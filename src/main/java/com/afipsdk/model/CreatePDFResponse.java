package com.afipsdk.model;

import com.google.gson.annotations.SerializedName;

/**
 * Respuesta de la operación de creación de PDF.
 */
public final class CreatePDFResponse {

    private String file = "";

    @SerializedName("file_name")
    private String fileName = "";

    /** URL o contenido del archivo PDF generado. */
    public String getFile() { return file; }

    /** Nombre del archivo PDF generado. */
    public String getFileName() { return fileName; }
}
