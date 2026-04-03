package com.flamingo.llp.monitor;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

public class SSEHandler {

    private final CopyOnWriteArrayList<OutputStream> clients = new CopyOnWriteArrayList<>();

    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        clients.add(os);
    }

    public void broadcast(String json) {
        for (OutputStream client : clients) {
            try {
                client.write(("data: " + json + "\n\n").getBytes());
                client.flush();
            } catch (IOException e) {
                clients.remove(client);
            }
        }
    }
}
