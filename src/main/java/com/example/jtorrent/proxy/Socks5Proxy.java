package com.example.jtorrent.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * SOCKS5 proxy implementation.
 */
public class Socks5Proxy {

    private static final byte SOCKS_VERSION = 0x05;
    private static final byte METHOD_NO_AUTH = 0x00;
    private static final byte METHOD_USERNAME_PASSWORD = 0x02;
    private static final byte CMD_CONNECT = 0x01;
    private static final byte ATYP_DOMAIN = 0x03;

    /**
     * Connect to target through SOCKS5 proxy.
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
            // 1. Send greeting
            if (config.hasAuthentication()) {
                out.write(new byte[] { SOCKS_VERSION, 0x02, METHOD_NO_AUTH, METHOD_USERNAME_PASSWORD });
            } else {
                out.write(new byte[] { SOCKS_VERSION, 0x01, METHOD_NO_AUTH });
            }
            out.flush();

            // 2. Receive method selection
            byte[] response = new byte[2];
            if (in.read(response) != 2) {
                throw new IOException("SOCKS5: Invalid greeting response");
            }

            if (response[0] != SOCKS_VERSION) {
                throw new IOException("SOCKS5: Invalid version in response");
            }

            byte selectedMethod = response[1];

            // 3. Authenticate if required
            if (selectedMethod == METHOD_USERNAME_PASSWORD) {
                if (!config.hasAuthentication()) {
                    throw new IOException("SOCKS5: Proxy requires authentication");
                }
                authenticateUsernamePassword(out, in, config.getUsername(), config.getPassword());
            } else if (selectedMethod != METHOD_NO_AUTH) {
                throw new IOException("SOCKS5: Unsupported authentication method: " + selectedMethod);
            }

            // 4. Send CONNECT request
            ByteBuffer request = ByteBuffer.allocate(256);
            request.put(SOCKS_VERSION);
            request.put(CMD_CONNECT);
            request.put((byte) 0x00); // Reserved

            // Domain name address type
            request.put(ATYP_DOMAIN);
            byte[] hostBytes = targetHost.getBytes("UTF-8");
            request.put((byte) hostBytes.length);
            request.put(hostBytes);
            request.putShort((short) targetPort);

            out.write(request.array(), 0, request.position());
            out.flush();

            // 5. Receive CONNECT response
            byte[] connectResponse = new byte[4];
            if (in.read(connectResponse) != 4) {
                throw new IOException("SOCKS5: Invalid CONNECT response");
            }

            if (connectResponse[0] != SOCKS_VERSION) {
                throw new IOException("SOCKS5: Invalid version in CONNECT response");
            }

            if (connectResponse[1] != 0x00) {
                throw new IOException("SOCKS5: CONNECT failed with code: " + connectResponse[1]);
            }

            // Read address (varies by type)
            byte addrType = connectResponse[3];
            if (addrType == 0x01) {
                // IPv4: 4 bytes + 2 port
                in.skip(6);
            } else if (addrType == 0x03) {
                // Domain: 1 length + N bytes + 2 port
                int len = in.read();
                in.skip(len + 2);
            } else if (addrType == 0x04) {
                // IPv6: 16 bytes + 2 port
                in.skip(18);
            }

            socket.setSoTimeout(0); // Reset timeout
            return socket;

        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static void authenticateUsernamePassword(OutputStream out, InputStream in,
            String username, String password) throws IOException {
        ByteBuffer auth = ByteBuffer.allocate(513);
        auth.put((byte) 0x01); // Version of username/password auth

        byte[] userBytes = username.getBytes("UTF-8");
        auth.put((byte) userBytes.length);
        auth.put(userBytes);

        byte[] passBytes = password.getBytes("UTF-8");
        auth.put((byte) passBytes.length);
        auth.put(passBytes);

        out.write(auth.array(), 0, auth.position());
        out.flush();

        byte[] authResponse = new byte[2];
        if (in.read(authResponse) != 2) {
            throw new IOException("SOCKS5: Invalid auth response");
        }

        if (authResponse[1] != 0x00) {
            throw new IOException("SOCKS5: Authentication failed");
        }
    }
}
