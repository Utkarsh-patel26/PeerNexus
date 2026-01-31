package com.example.jtorrent.streaming;

import com.example.jtorrent.logging.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpStreamServer {

    private static final Logger logger = Logger.getLogger(HttpStreamServer.class);
    private static final int CHUNK_SIZE = 256 * 1024;

    private final int port;
    private final StreamingManager streamingManager;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;

    public HttpStreamServer(int port, StreamingManager streamingManager) {
        this.port = port;
        this.streamingManager = streamingManager;
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(executor);
        server.createContext("/stream", new StreamHandler());
        server.start();

        logger.info("HTTP stream server started on port %d", port);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        if (server != null) {
            server.stop(0);
        }
        executor.shutdown();
        logger.info("HTTP stream server stopped");
    }

    private class StreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");

            if (parts.length < 4) {
                sendError(exchange, 400, "Invalid path. Use /stream/{hash}/{fileIndex}");
                return;
            }

            String hash = parts[2];
            int fileIndex;
            try {
                fileIndex = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid file index");
                return;
            }

            try {
                handleStreamRequest(exchange, hash, fileIndex);
            } catch (Exception e) {
                logger.error("Stream error: %s", e.getMessage());
                sendError(exchange, 500, e.getMessage());
            }
        }

        private void handleStreamRequest(HttpExchange exchange, String hash, int fileIndex) throws Exception {
            long fileSize = streamingManager.getFileSize(hash, fileIndex);
            if (fileSize < 0) {
                sendError(exchange, 404, "File not found");
                return;
            }

            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            long start = 0;
            long end = fileSize - 1;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String range = rangeHeader.substring(6);
                String[] rangeParts = range.split("-");
                start = Long.parseLong(rangeParts[0]);
                if (rangeParts.length > 1 && !rangeParts[1].isEmpty()) {
                    end = Long.parseLong(rangeParts[1]);
                }
            }

            if (start >= fileSize || end >= fileSize || start > end) {
                exchange.sendResponseHeaders(416, 0);
                exchange.getResponseBody().close();
                return;
            }

            long contentLength = end - start + 1;

            exchange.getResponseHeaders().set("Content-Type", getMimeType(fileIndex));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
            exchange.getResponseHeaders().set("Content-Range",
                    String.format("bytes %d-%d/%d", start, end, fileSize));
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            int statusCode = (rangeHeader != null) ? 206 : 200;
            exchange.sendResponseHeaders(statusCode, contentLength);

            try (OutputStream out = exchange.getResponseBody()) {
                long remaining = contentLength;
                long offset = start;

                while (remaining > 0 && running.get()) {
                    int toRead = (int) Math.min(CHUNK_SIZE, remaining);
                    byte[] data = streamingManager.readData(hash, fileIndex, offset, toRead);
                    out.write(data);
                    out.flush();
                    offset += data.length;
                    remaining -= data.length;
                }
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes();
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        }

        private String getMimeType(int fileIndex) {
            return "application/octet-stream";
        }
    }
}
