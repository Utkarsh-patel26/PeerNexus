package com.example.jtorrent.network;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class IPv6Support implements AutoCloseable {
    private final int port;
    private final List<ServerSocketChannel> boundChannels = new CopyOnWriteArrayList<>();
    private volatile boolean dualStack;
    private volatile Inet6Address preferredAddress;

    public IPv6Support(int port) {
        this.port = port;
    }

    public void bind() throws IOException {
        if (isDualStackSupported()) {
            bindDualStack();
        } else {
            bindSeparate();
        }
    }

    private void bindDualStack() throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.INET6);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        try {
            // IP_V6_ONLY is platform-specific, use separate bindings on Windows
            channel.bind(new InetSocketAddress(port));
            boundChannels.add(channel);
            dualStack = true;
        } catch (UnsupportedOperationException | IOException e) {
            channel.close();
            bindSeparate();
        }
    }

    private void bindSeparate() throws IOException {
        ServerSocketChannel ipv6 = ServerSocketChannel.open(StandardProtocolFamily.INET6);
        ipv6.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ipv6.bind(new InetSocketAddress(port));
        boundChannels.add(ipv6);

        ServerSocketChannel ipv4 = ServerSocketChannel.open(StandardProtocolFamily.INET);
        ipv4.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ipv4.bind(new InetSocketAddress(port));
        boundChannels.add(ipv4);

        dualStack = false;
    }

    public List<ServerSocketChannel> getBoundChannels() {
        return Collections.unmodifiableList(boundChannels);
    }

    public boolean isDualStack() {
        return dualStack;
    }

    public List<InetAddress> getLocalAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    addresses.add(addrs.nextElement());
                }
            }
        } catch (SocketException ignored) {
        }
        return addresses;
    }

    public List<Inet6Address> getGlobalIPv6Addresses() {
        return getLocalAddresses().stream()
                .filter(Inet6Address.class::isInstance)
                .map(Inet6Address.class::cast)
                .filter(addr -> !addr.isLinkLocalAddress())
                .filter(addr -> !addr.isSiteLocalAddress())
                .filter(addr -> !addr.isLoopbackAddress())
                .toList();
    }

    public Optional<Inet6Address> getPreferredIPv6Address() {
        if (preferredAddress != null) {
            return Optional.of(preferredAddress);
        }
        List<Inet6Address> globals = getGlobalIPv6Addresses();
        if (!globals.isEmpty()) {
            return Optional.of(globals.getFirst());
        }
        return getLocalAddresses().stream()
                .filter(Inet6Address.class::isInstance)
                .map(Inet6Address.class::cast)
                .filter(addr -> !addr.isLoopbackAddress())
                .findFirst();
    }

    public void setPreferredAddress(Inet6Address address) {
        this.preferredAddress = address;
    }

    public static boolean isDualStackSupported() {
        // On most systems, IPv6 sockets can accept IPv4 connections by default
        try (ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.INET6)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isIPv6Available() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    if (addrs.nextElement() instanceof Inet6Address) {
                        return true;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return false;
    }

    public static Inet6Address toIPv4MappedIPv6(Inet4Address ipv4) {
        byte[] v4Bytes = ipv4.getAddress();
        byte[] v6Bytes = new byte[16];
        v6Bytes[10] = (byte) 0xFF;
        v6Bytes[11] = (byte) 0xFF;
        System.arraycopy(v4Bytes, 0, v6Bytes, 12, 4);
        try {
            InetAddress result = InetAddress.getByAddress(v6Bytes);
            if (result instanceof Inet6Address) {
                return (Inet6Address) result;
            }
            // Java detects IPv4-mapped addresses and returns Inet4Address
            // Use the overload with scope_id to force Inet6Address
            return Inet6Address.getByAddress(null, v6Bytes, null);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Optional<Inet4Address> extractIPv4(Inet6Address ipv6) {
        byte[] bytes = ipv6.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return Optional.empty();
            }
        }
        if (bytes[10] != (byte) 0xFF || bytes[11] != (byte) 0xFF) {
            return Optional.empty();
        }
        byte[] v4Bytes = new byte[4];
        System.arraycopy(bytes, 12, v4Bytes, 0, 4);
        try {
            return Optional.of((Inet4Address) InetAddress.getByAddress(v4Bytes));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        for (ServerSocketChannel channel : boundChannels) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
        boundChannels.clear();
    }
}
