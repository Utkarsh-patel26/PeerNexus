package com.example.jtorrent.web;

import com.example.jtorrent.events.EventBus;
import com.example.jtorrent.events.TransferStatsEvent;
import com.example.jtorrent.events.TorrentEvent;
import com.example.jtorrent.logging.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketStatsPublisher {

    private static final Logger logger = Logger.getLogger(WebSocketStatsPublisher.class);

    private final int port;
    private final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public WebSocketStatsPublisher(int port) {
        this.port = port;
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        EventBus.getInstance().subscribe(TransferStatsEvent.class, this::onTransferStats);
        EventBus.getInstance().subscribe(TorrentEvent.class, this::onTorrentEvent);
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            acceptThread = new Thread(this::acceptLoop, "WebSocket-Accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            logger.info("WebSocket server started on port %d", port);
        } catch (IOException e) {
            logger.error("Failed to start WebSocket server: %s", e.getMessage());
            running.set(false);
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        connections.forEach(WebSocketConnection::close);
        connections.clear();
        executor.shutdown();
    }

    public void broadcast(String message) {
        byte[] frame = createTextFrame(message);
        connections.removeIf(conn -> !conn.send(frame));
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Accept error: %s", e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            if (performHandshake(socket)) {
                WebSocketConnection conn = new WebSocketConnection(socket);
                connections.add(conn);
                conn.readLoop(this::onMessage, () -> connections.remove(conn));
            }
        } catch (IOException e) {
            logger.debug("WebSocket connection error: %s", e.getMessage());
        }
    }

    private boolean performHandshake(Socket socket) throws IOException {
        byte[] buffer = new byte[4096];
        int read = socket.getInputStream().read(buffer);
        if (read <= 0)
            return false;

        String request = new String(buffer, 0, read, StandardCharsets.UTF_8);
        String key = extractWebSocketKey(request);
        if (key == null)
            return false;

        String acceptKey = generateAcceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private String extractWebSocketKey(String request) {
        for (String line : request.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                return line.substring(18).trim();
            }
        }
        return null;
    }

    private String generateAcceptKey(String key) {
        try {
            String combined = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTextFrame(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        int length = payload.length;

        byte[] frame;
        if (length <= 125) {
            frame = new byte[2 + length];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) length;
            System.arraycopy(payload, 0, frame, 2, length);
        } else if (length <= 65535) {
            frame = new byte[4 + length];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) 126;
            frame[2] = (byte) ((length >> 8) & 0xFF);
            frame[3] = (byte) (length & 0xFF);
            System.arraycopy(payload, 0, frame, 4, length);
        } else {
            frame = new byte[10 + length];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) 127;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) ((length >> (56 - i * 8)) & 0xFF);
            }
            System.arraycopy(payload, 0, frame, 10, length);
        }
        return frame;
    }

    private void onMessage(String message) {
        logger.debug("WebSocket received: %s", message);
    }

    private void onTransferStats(TransferStatsEvent event) {
        String json = String.format(
                "{\"type\":\"stats\",\"downloadRate\":%d,\"uploadRate\":%d,\"downloaded\":%d,\"uploaded\":%d}",
                event.getDownloadRate(), event.getUploadRate(), event.getDownloadedBytes(), event.getUploadedBytes());
        broadcast(json);
    }

    private void onTorrentEvent(TorrentEvent event) {
        String json = String.format("{\"type\":\"torrent\",\"event\":\"%s\",\"hash\":\"%s\"}",
                event.getClass().getSimpleName(), bytesToHex(event.getInfoHash()));
        broadcast(json);
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class WebSocketConnection {
        private final Socket socket;
        private final OutputStream out;

        WebSocketConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
        }

        boolean send(byte[] frame) {
            try {
                synchronized (out) {
                    out.write(frame);
                    out.flush();
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        void readLoop(java.util.function.Consumer<String> onMessage, Runnable onClose) {
            try {
                byte[] buffer = new byte[8192];
                while (!socket.isClosed()) {
                    int read = socket.getInputStream().read(buffer);
                    if (read <= 0)
                        break;
                    String message = decodeFrame(buffer, read);
                    if (message != null) {
                        onMessage.accept(message);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                close();
                onClose.run();
            }
        }

        private String decodeFrame(byte[] buffer, int length) {
            if (length < 2)
                return null;
            int payloadLength = buffer[1] & 0x7F;
            int maskStart = 2;

            if (payloadLength == 126) {
                payloadLength = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                maskStart = 4;
            } else if (payloadLength == 127) {
                maskStart = 10;
            }

            byte[] mask = new byte[4];
            System.arraycopy(buffer, maskStart, mask, 0, 4);

            byte[] payload = new byte[payloadLength];
            for (int i = 0; i < payloadLength; i++) {
                payload[i] = (byte) (buffer[maskStart + 4 + i] ^ mask[i % 4]);
            }
            return new String(payload, StandardCharsets.UTF_8);
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
