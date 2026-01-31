package com.example.jtorrent.proxy;

/**
 * Proxy configuration.
 */
public class ProxyConfig {

    private final ProxyType type;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    /**
     * Create proxy configuration without authentication.
     */
    public ProxyConfig(ProxyType type, String host, int port) {
        this(type, host, port, null, null);
    }

    /**
     * Create proxy configuration with authentication.
     */
    public ProxyConfig(ProxyType type, String host, int port, String username, String password) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public ProxyType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasAuthentication() {
        return username != null && !username.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%s://%s:%d%s",
                type.name().toLowerCase(),
                host,
                port,
                hasAuthentication() ? " (authenticated)" : "");
    }
}
