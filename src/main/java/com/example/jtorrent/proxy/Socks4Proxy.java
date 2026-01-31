package com.example.jtorrent.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * SOCKS4 proxy implementation.
 */
public class Socks4Proxy {

    private static final byte SOCKS_VERSION = 0x04;
    private static final byte CMD_CONNECT = 0x01;

    /**
     * Connect to target through SOCKS4 proxy.
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
        InputStream in = socket.getInputStream();

        try {
            // Resolve target host to IP (SOCKS4 requires IP)
            InetAddress targetAddress = InetAddress.getByName(targetHost);
            byte[] targetIp = targetAddress.getAddress();

            if (targetIp.length != 4) {
                throw new IOException("SOCKS4: IPv6 addresses not supported");
            }

            // Build request
            ByteBuffer request = ByteBuffer
                    .allocate(9 + (config.hasAuthentication() ? config.getUsername().length() : 0));
            request.put(SOCKS_VERSION);
            request.put(CMD_CONNECT);
            request.putShort((short) targetPort);
            request.put(targetIp);

            // User ID (empty if no auth)
            if (config.hasAuthentication()) {
                request.put(config.getUsername().getBytes("UTF-8"));
            }
            request.put((byte) 0x00); // Null terminator

            out.write(request.array(), 0, request.position());
            out.flush();

            // Read response
            byte[] response = new byte[8];
            if (in.read(response) != 8) {
                throw new IOException("SOCKS4: Invalid response");
            }

            if (response[1] != 0x5A) {
                throw new IOException("SOCKS4: Connection rejected with code: " + response[1]);
            }

            socket.setSoTimeout(0); // Reset timeout
            return socket;

        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }
}
