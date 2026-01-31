package com.example.jtorrent.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP CONNECT proxy implementation.
 */
public class HttpProxy {

    /**
     * Connect to target through HTTP CONNECT proxy.
     *
     * @param config     proxy configuration
     * @param targetHost target host
     * @param targetPort target port
     * @param timeout    connection timeout
     * @return connected socket
     * @throws IOException if connection fails
     */
    public static Socket connect(ProxyConfig config, String targetHost, int targetPort, int timeout)
            throws IOException {

        // Connect to proxy
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), timeout);
        socket.setSoTimeout(timeout);

        OutputStream out = socket.getOutputStream();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        try {
            // Build CONNECT request
            StringBuilder request = new StringBuilder();
            request.append("CONNECT ").append(targetHost).append(":").append(targetPort).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(targetHost).append(":").append(targetPort).append("\r\n");

            // Add authentication if configured
            if (config.hasAuthentication()) {
                String credentials = config.getUsername() + ":" + config.getPassword();
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                request.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
            }

            request.append("\r\n");

            // Send request
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Read response status line
            String statusLine = in.readLine();
            if (statusLine == null) {
                throw new IOException("HTTP: No response from proxy");
            }

            // Parse status code
            String[] parts = statusLine.split(" ", 3);
            if (parts.length < 2) {
                throw new IOException("HTTP: Invalid response: " + statusLine);
            }

            int statusCode;
            try {
                statusCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("HTTP: Invalid status code: " + parts[1]);
            }

            if (statusCode != 200) {
                throw new IOException("HTTP: CONNECT failed with status: " + statusCode);
            }

            // Skip response headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Skip headers
            }

            socket.setSoTimeout(0); // Reset timeout
            return socket;

        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }
}
