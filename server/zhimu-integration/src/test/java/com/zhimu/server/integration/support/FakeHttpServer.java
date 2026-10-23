package com.zhimu.server.integration.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * 测试用本地 HTTP 服务（JDK 自带 HttpServer）：记录请求，按 handler 返回响应。
 */
public final class FakeHttpServer implements AutoCloseable {

    public record Recorded(String method, String path, String rawQuery, Map<String, List<String>> headers,
                           String body) {

        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                    return e.getValue().getFirst();
                }
            }
            return null;
        }
    }

    @FunctionalInterface
    public interface Handler {
        void handle(Recorded request, HttpExchange exchange) throws Exception;
    }

    private final HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private volatile Handler handler;

    private FakeHttpServer(Handler handler) throws IOException {
        this.handler = handler;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/", ex -> {
            try {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Recorded r = new Recorded(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                        ex.getRequestURI().getRawQuery(), Map.copyOf(ex.getRequestHeaders()), body);
                requests.add(r);
                this.handler.handle(r, ex);
            } catch (Exception e) {
                // 客户端断开等
            } finally {
                ex.close();
            }
        });
        this.server.start();
    }

    public static FakeHttpServer start(Handler handler) throws IOException {
        return new FakeHttpServer(handler);
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void handler(Handler handler) {
        this.handler = handler;
    }

    public List<Recorded> requests() {
        return new ArrayList<>(requests);
    }

    public List<Recorded> requests(String pathPrefix) {
        return requests.stream().filter(r -> r.path().startsWith(pathPrefix)).toList();
    }

    public static void json(HttpExchange ex, int status, String body) throws IOException {
        respond(ex, status, "application/json;charset=UTF-8", body);
    }

    public static void respond(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /** 以 text/event-stream 逐块写出（每块之间等待 delayMs） */
    public static void sse(HttpExchange ex, List<String> chunks, long delayMs) throws Exception {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
        ex.sendResponseHeaders(200, 0);
        OutputStream os = ex.getResponseBody();
        for (String chunk : chunks) {
            os.write(chunk.getBytes(StandardCharsets.UTF_8));
            os.flush();
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        }
        os.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
