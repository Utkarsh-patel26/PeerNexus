package com.example.jtorrent.network;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PcpManager implements AutoCloseable {
    private static final int PCP_PORT = 5351;
    private static final byte VERSION = 2;
    private static final byte OP_MAP = 1;
    private static final byte OP_PEER = 2;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final int MAX_RETRIES = 3;

    private final InetAddress pcpServer;
    private final InetAddress clientAddress;
    private final DatagramChannel channel;
    private final Map<MappingKey, ActiveMapping> activeMappings = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final byte[] nonce = new byte[12];

    public PcpManager(InetAddress pcpServer, InetAddress clientAddress) throws IOException {
        this.pcpServer = pcpServer;
        this.clientAddress = clientAddress;
        this.channel = DatagramChannel.open();
        this.channel.configureBlocking(false);
        new SecureRandom().nextBytes(nonce);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pcp-manager");
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

    public CompletableFuture<MappingResult> mapPort(Protocol protocol, int internalPort,
            int externalPort, Duration lifetime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int lifetimeSeconds = (int) lifetime.toSeconds();
                ByteBuffer request = createMapRequest(protocol, internalPort,
                        externalPort, lifetimeSeconds);

                ByteBuffer response = sendAndReceive(request, 60);
                if (response == null) {
                    return new MappingResult(false, null, 0, 0, "Timeout");
                }

                return parseMapResponse(response, protocol, internalPort);
            } catch (IOException e) {
                return new MappingResult(false, null, 0, 0, e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Boolean> unmapPort(Protocol protocol, int internalPort) {
        return mapPort(protocol, internalPort, 0, Duration.ZERO)
                .thenApply(result -> {
                    activeMappings.remove(new MappingKey(protocol, internalPort));
                    return true;
                });
    }

    public Map<MappingKey, ActiveMapping> getActiveMappings() {
        return Collections.unmodifiableMap(activeMappings);
    }

    private ByteBuffer createMapRequest(Protocol protocol, int internalPort,
            int externalPort, int lifetime) {
        ByteBuffer request = ByteBuffer.allocate(60);
        request.put(VERSION);
        request.put(OP_MAP);
        request.putShort((short) 0);
        request.putInt(lifetime);

        byte[] clientAddr = toIPv6Mapped(clientAddress.getAddress());
        request.put(clientAddr);

        request.put(nonce);
        request.put(protocol == Protocol.TCP ? (byte) 6 : (byte) 17);
        request.put((byte) 0);
        request.put((byte) 0);
        request.put((byte) 0);
        request.putShort((short) internalPort);
        request.putShort((short) externalPort);

        byte[] suggestedExternal = new byte[16];
        request.put(suggestedExternal);

        request.flip();
        return request;
    }

    private MappingResult parseMapResponse(ByteBuffer response, Protocol protocol,
            int internalPort) throws UnknownHostException {
        byte version = response.get();
        byte opcode = response.get();
        response.get();
        byte resultCode = response.get();

        if (resultCode != 0) {
            return new MappingResult(false, null, 0, 0,
                    "PCP error: " + resultCode);
        }

        int lifetime = response.getInt();
        response.position(response.position() + 4);

        response.position(response.position() + 12);

        response.get();
        response.get();
        response.get();
        response.get();
        int mappedInternalPort = response.getShort() & 0xFFFF;
        int assignedExternalPort = response.getShort() & 0xFFFF;

        byte[] externalAddrBytes = new byte[16];
        response.get(externalAddrBytes);
        InetAddress externalAddress = extractAddress(externalAddrBytes);

        MappingKey key = new MappingKey(protocol, internalPort);
        activeMappings.put(key, new ActiveMapping(
                mappedInternalPort, assignedExternalPort, externalAddress,
                Instant.now().plusSeconds(lifetime)));

        return new MappingResult(true, externalAddress, assignedExternalPort,
                lifetime, null);
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
        SocketAddress target = new InetSocketAddress(pcpServer, PCP_PORT);
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

    private byte[] toIPv6Mapped(byte[] addr) {
        if (addr.length == 16) {
            return addr;
        }
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xFF;
        mapped[11] = (byte) 0xFF;
        System.arraycopy(addr, 0, mapped, 12, 4);
        return mapped;
    }

    private InetAddress extractAddress(byte[] bytes) throws UnknownHostException {
        boolean isIPv4Mapped = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                isIPv4Mapped = false;
                break;
            }
        }
        if (isIPv4Mapped && bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) {
            byte[] ipv4 = new byte[4];
            System.arraycopy(bytes, 12, ipv4, 0, 4);
            return InetAddress.getByAddress(ipv4);
        }
        return InetAddress.getByAddress(bytes);
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

    public enum Protocol {
        TCP, UDP
    }

    public record MappingKey(Protocol protocol, int internalPort) {
    }

    public record ActiveMapping(int internalPort, int externalPort,
            InetAddress externalAddress, Instant expiresAt) {
    }

    public record MappingResult(boolean success, InetAddress externalAddress,
            int externalPort, int lifetime, String error) {
    }
}
