package com.example.jtorrent.network;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NatPmpManager implements AutoCloseable {
    private static final int NAT_PMP_PORT = 5351;
    private static final byte VERSION = 0;
    private static final byte OP_EXTERNAL_IP = 0;
    private static final byte OP_MAP_UDP = 1;
    private static final byte OP_MAP_TCP = 2;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final int MAX_RETRIES = 3;

    private final InetAddress gatewayAddress;
    private final DatagramChannel channel;
    private final Map<MappingKey, ActiveMapping> activeMappings = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NatPmpManager(InetAddress gatewayAddress) throws IOException {
        this.gatewayAddress = gatewayAddress;
        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(false);
        this.channel.socket().setSoTimeout((int) REQUEST_TIMEOUT.toMillis());
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nat-pmp-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.scheduleAtFixedRate(this::refreshMappings,
                    60, 60, TimeUnit.SECONDS);
        }
    }

    public CompletableFuture<InetAddress> getExternalAddress() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ByteBuffer request = ByteBuffer.allocate(2);
                request.put(VERSION);
                request.put(OP_EXTERNAL_IP);
                request.flip();

                ByteBuffer response = sendAndReceive(request, 12);
                if (response == null) {
                    return null;
                }

                response.position(8);
                byte[] addr = new byte[4];
                response.get(addr);
                return InetAddress.getByAddress(addr);
            } catch (IOException e) {
                return null;
            }
        }, executor);
    }

    public CompletableFuture<MappingResult> mapPort(Protocol protocol, int internalPort,
            int externalPort, Duration lifetime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int lifetimeSeconds = (int) lifetime.toSeconds();
                ByteBuffer request = ByteBuffer.allocate(12);
                request.put(VERSION);
                request.put(protocol == Protocol.TCP ? OP_MAP_TCP : OP_MAP_UDP);
                request.putShort((short) 0);
                request.putShort((short) internalPort);
                request.putShort((short) externalPort);
                request.putInt(lifetimeSeconds);
                request.flip();

                ByteBuffer response = sendAndReceive(request, 16);
                if (response == null) {
                    return new MappingResult(false, 0, 0, null);
                }

                response.position(2);
                int resultCode = response.getShort() & 0xFFFF;
                if (resultCode != 0) {
                    return new MappingResult(false, 0, 0, "Error code: " + resultCode);
                }

                response.position(8);
                int mappedInternal = response.getShort() & 0xFFFF;
                int mappedExternal = response.getShort() & 0xFFFF;
                int mappedLifetime = response.getInt();

                MappingKey key = new MappingKey(protocol, internalPort);
                activeMappings.put(key, new ActiveMapping(
                        mappedInternal, mappedExternal,
                        Instant.now().plusSeconds(mappedLifetime)));

                return new MappingResult(true, mappedExternal, mappedLifetime, null);
            } catch (IOException e) {
                return new MappingResult(false, 0, 0, e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Boolean> unmapPort(Protocol protocol, int internalPort) {
        return mapPort(protocol, internalPort, 0, Duration.ZERO)
                .thenApply(result -> {
                    if (result.success()) {
                        activeMappings.remove(new MappingKey(protocol, internalPort));
                    }
                    return result.success();
                });
    }

    public Map<MappingKey, ActiveMapping> getActiveMappings() {
        return Collections.unmodifiableMap(activeMappings);
    }

    private void refreshMappings() {
        if (!running.get()) {
            return;
        }
        Instant now = Instant.now();
        Instant refreshThreshold = now.plus(REFRESH_BEFORE_EXPIRY);

        activeMappings.forEach((key, mapping) -> {
            if (mapping.expiresAt().isBefore(refreshThreshold)) {
                Duration remaining = Duration.between(now, mapping.expiresAt());
                Duration newLifetime = Duration.ofSeconds(
                        Math.max(3600, remaining.toSeconds() * 2));
                mapPort(key.protocol(), mapping.internalPort(),
                        mapping.externalPort(), newLifetime);
            }
        });
    }

    private ByteBuffer sendAndReceive(ByteBuffer request, int responseSize) throws IOException {
        SocketAddress target = new InetSocketAddress(gatewayAddress, NAT_PMP_PORT);
        ByteBuffer response = ByteBuffer.allocate(responseSize);

        for (int i = 0; i < MAX_RETRIES; i++) {
            request.rewind();
            channel.send(request, target);

            long deadline = System.currentTimeMillis() + REQUEST_TIMEOUT.toMillis();
            while (System.currentTimeMillis() < deadline) {
                response.clear();
                SocketAddress from = channel.receive(response);
                if (from != null) {
                    response.flip();
                    return response;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdown();
        try {
            activeMappings.keySet().forEach(key -> unmapPort(key.protocol(), key.internalPort()).join());
            channel.close();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Optional<InetAddress> discoverGateway() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        byte[] bytes = addr.getAddress();
                        bytes[3] = 1;
                        return Optional.of(InetAddress.getByAddress(bytes));
                    }
                }
            }
        } catch (SocketException | UnknownHostException e) {
            // Ignore
        }
        return Optional.empty();
    }

    public enum Protocol {
        TCP, UDP
    }

    public record MappingKey(Protocol protocol, int internalPort) {
    }

    public record ActiveMapping(int internalPort, int externalPort, Instant expiresAt) {
    }

    public record MappingResult(boolean success, int externalPort, int lifetime, String error) {
    }
}
