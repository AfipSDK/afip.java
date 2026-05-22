package com.afipsdk;

import com.afipsdk.http.HttpClient;
import com.afipsdk.model.AfipOptions;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * HttpClient falso para tests unitarios. Las respuestas se encolan en orden
 * y se consumen con cada llamada a get() o post().
 * Soporta encolar tanto strings JSON como excepciones.
 */
class StubHttpClient extends HttpClient {

    private final Queue<Object> responses = new LinkedList<Object>();

    StubHttpClient() {
        super(new AfipOptions());
    }

    void addResponse(String json) {
        responses.add(json);
    }

    void addException(RuntimeException e) {
        responses.add(e);
    }

    @Override
    public String get(String path) {
        return poll();
    }

    @Override
    public String post(String path, Map<String, Object> body) {
        return poll();
    }

    private String poll() {
        Object item = responses.poll();
        if (item == null) return "{}";
        if (item instanceof RuntimeException) throw (RuntimeException) item;
        return (String) item;
    }
}
