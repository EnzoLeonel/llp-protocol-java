package com.flamingo.llp.monitor;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MonitorServer {

    private final HttpServer server;

    public MonitorServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {

            try (var is = getClass().getClassLoader()
                    .getResourceAsStream("monitor/monitor.html")) {

                if (is == null) {
                    String msg = "monitor.html not found";
                    exchange.sendResponseHeaders(404, msg.length());
                    exchange.getResponseBody().write(msg.getBytes());
                    exchange.close();
                    return;
                }

                byte[] html = is.readAllBytes();

                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);

            } catch (Exception e) {
                String msg = "Internal error: " + e.getMessage();
                exchange.sendResponseHeaders(500, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
            } finally {
                exchange.close();
            }
        });
    }

    public void start() {
        server.start();
        System.out.println("🌐 Monitor running at http://localhost:" + server.getAddress().getPort());
    }

    public HttpServer getServer() {
        return server;
    }

    public void stop() {
        server.stop(0);
    }
}