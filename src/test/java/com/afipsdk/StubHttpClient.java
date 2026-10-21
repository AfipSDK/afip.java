package com.afipsdk;

import com.afipsdk.http.AfipHttpResponse;
import com.afipsdk.http.HttpClient;
import com.afipsdk.model.AfipOptions;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * HttpClient falso para tests unitarios. Las respuestas se encolan en orden
 * y se consumen con cada llamada a get(), post() o request().
 * Soporta encolar tanto strings JSON (con status opcional) como excepciones,
 * y registra las requests recibidas.
 */
class StubHttpClient extends HttpClient {

    /** Request recibida por el stub. */
    static final class Call {
        final String method;
        final String path;
        final Map<String, Object> body;
        final Map<String, String> headers;

        Call(String method, String path, Map<String, Object> body, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.body = body;
            this.headers = headers;
        }
    }

    private final Queue<Object> responses = new LinkedList<Object>();
    private final List<Call> calls = new ArrayList<Call>();

    StubHttpClient() {
        super(new AfipOptions());
    }

    void addResponse(String json) {
        responses.add(json);
    }

    void addResponse(int status, String json) {
        responses.add(new AfipHttpResponse(status, "", json));
    }

    void addException(RuntimeException e) {
        responses.add(e);
    }

    List<Call> getCalls() {
        return calls;
    }

    Call lastCall() {
        return calls.get(calls.size() - 1);
    }

    @Override
    public String get(String path) {
        calls.add(new Call("GET", path, null, null));
        return poll();
    }

    @Override
    public String post(String path, Map<String, Object> body) {
        calls.add(new Call("POST", path, body, null));
        return poll();
    }

    @Override
    public AfipHttpResponse request(String method, String path, Map<String, Object> body, Map<String, String> headers) {
        calls.add(new Call(method, path, body, headers));
        Object item = responses.poll();
        if (item == null) return new AfipHttpResponse(200, "", "{}");
        if (item instanceof RuntimeException) throw (RuntimeException) item;
        if (item instanceof AfipHttpResponse) return (AfipHttpResponse) item;
        return new AfipHttpResponse(200, "", (String) item);
    }

    private String poll() {
        Object item = responses.poll();
        if (item == null) return "{}";
        if (item instanceof RuntimeException) throw (RuntimeException) item;
        if (item instanceof AfipHttpResponse) return ((AfipHttpResponse) item).getData();
        return (String) item;
    }
}
