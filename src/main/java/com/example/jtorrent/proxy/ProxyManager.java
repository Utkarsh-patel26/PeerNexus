package com.example.jtorrent.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Factory for creating proxy-aware sockets.
 * Supports SOCKS4, SOCKS5, and HTTP CONNECT proxies.
 */
public class ProxyManager {

    private ProxyConfig proxyConfig;

    public ProxyManager() {
        this.proxyConfig = null; // No proxy by default
    }

    public ProxyManager(ProxyConfig config) {
        this.proxyConfig = config;
    }

    /**
     * Set proxy configuration.
     *
     * @param config proxy configuration (null to disable)
     */
    public void setProxyConfig(ProxyConfig config) {
        this.proxyConfig = config;
    }

    /**
     * Get current proxy configuration.
     */
    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    /**
     * Create a socket connection through proxy if configured.
     *
     * @param targetHost target host
     * @param targetPort target port
     * @param timeout    connection timeout in milliseconds
     * @return connected socket
     * @throws IOException if connection fails
     */
    public Socket createConnection(String targetHost, int targetPort, int timeout) throws IOException {
        if (proxyConfig == null || proxyConfig.getType() == ProxyType.NONE) {
            // Direct connection
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(targetHost, targetPort), timeout);
            return socket;
        }

        switch (proxyConfig.getType()) {
            case SOCKS4:
                return Socks4Proxy.connect(proxyConfig, targetHost, targetPort, timeout);
            case SOCKS5:
                return Socks5Proxy.connect(proxyConfig, targetHost, targetPort, timeout);
            case HTTP:
                return HttpProxy.connect(proxyConfig, targetHost, targetPort, timeout);
            default:
                throw new IOException("Unsupported proxy type: " + proxyConfig.getType());
        }
    }

    /**
     * Test proxy connectivity.
     *
     * @return true if proxy is reachable
     */
    public boolean testConnection() {
        if (proxyConfig == null || proxyConfig.getType() == ProxyType.NONE) {
            return true; // No proxy configured
        }

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()),
                    5000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
