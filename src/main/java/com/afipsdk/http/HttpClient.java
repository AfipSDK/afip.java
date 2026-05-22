package com.afipsdk.http;

import com.afipsdk.exception.AfipException;
import com.afipsdk.model.AfipOptions;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Cliente HTTP interno. Wrapper sobre {@link HttpURLConnection}.
 * No forma parte de la API pública del SDK.
 */
public class HttpClient {

    private static final String SDK_VERSION = "1.0.0";
    private static final String API_BASE_URL = "https://app.afipsdk.com/api/";
    private static final int TIMEOUT_MS = 30_000;

    private static final Gson GSON = new Gson();

    private final AfipOptions options;

    public HttpClient(AfipOptions options) {
        this.options = options;
    }

    /**
     * Ejecuta un GET y devuelve el cuerpo de la respuesta como JSON string.
     */
    public String get(String path) {
        AfipHttpResponse response = execute("GET", path, null);
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new AfipException("Response status code does not indicate success: "
                    + response.getStatus() + " (" + response.getStatusText() + "). Body: " + response.getData());
        }
        return response.getData();
    }

    /**
     * Ejecuta un POST con body JSON y devuelve el cuerpo de la respuesta como JSON string.
     */
    public String post(String path, Map<String, Object> body) {
        AfipHttpResponse response = execute("POST", path, body);
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new AfipException("Response status code does not indicate success: "
                    + response.getStatus() + " (" + response.getStatusText() + "). Body: " + response.getData());
        }
        return response.getData();
    }

    private AfipHttpResponse execute(String method, String path, Map<String, Object> body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "afip-sdk-java/" + SDK_VERSION);
            conn.setRequestProperty("sdk-version-number", SDK_VERSION);
            conn.setRequestProperty("sdk-library", "java");
            conn.setRequestProperty("sdk-environment", options.isProduction() ? "prod" : "dev");

            String token = options.getAccessToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                byte[] json = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(json.length);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(json);
                } finally {
                    os.close();
                }
            }

            int status = conn.getResponseCode();
            String statusText = conn.getResponseMessage();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String responseBody = readStream(stream);

            return new AfipHttpResponse(status, statusText != null ? statusText : "", responseBody);

        } catch (AfipException e) {
            throw e;
        } catch (Exception e) {
            throw new AfipException("Error communicating with AfipSDK: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } finally {
            reader.close();
        }
    }
}
