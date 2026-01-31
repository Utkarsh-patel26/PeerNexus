package com.example.jtorrent.network;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class PortMappingService implements AutoCloseable {
    private final int port;
    private final AtomicReference<MappingMethod> activeMethod = new AtomicReference<>();
    private NatPmpManager natPmpManager;
    private PcpManager pcpManager;
    private volatile InetAddress externalAddress;
    private volatile int mappedPort;

    public PortMappingService(int port) {
        this.port = port;
    }

    public CompletableFuture<MappingResult> mapPort(Duration lifetime) {
        return tryNatPmp(lifetime)
                .thenCompose(result -> {
                    if (result.success()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return tryPcp(lifetime);
                })
                .thenCompose(result -> {
                    if (result.success()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return tryUpnp(lifetime);
                });
    }

    private CompletableFuture<MappingResult> tryNatPmp(Duration lifetime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<InetAddress> gateway = NatPmpManager.discoverGateway();
                if (gateway.isEmpty()) {
                    return new MappingResult(false, null, 0, "No gateway found");
                }

                natPmpManager = new NatPmpManager(gateway.get());
                natPmpManager.start();

                NatPmpManager.MappingResult natResult = natPmpManager
                        .mapPort(NatPmpManager.Protocol.TCP, port, port, lifetime)
                        .join();

                if (natResult.success()) {
                    activeMethod.set(MappingMethod.NAT_PMP);
                    mappedPort = natResult.externalPort();
                    InetAddress ext = natPmpManager.getExternalAddress().join();
                    externalAddress = ext;
                    return new MappingResult(true, ext, natResult.externalPort(), null);
                }
                return new MappingResult(false, null, 0, natResult.error());
            } catch (IOException e) {
                return new MappingResult(false, null, 0, e.getMessage());
            }
        });
    }

    private CompletableFuture<MappingResult> tryPcp(Duration lifetime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<InetAddress> gateway = NatPmpManager.discoverGateway();
                if (gateway.isEmpty()) {
                    return new MappingResult(false, null, 0, "No gateway found");
                }

                InetAddress localAddr = InetAddress.getLocalHost();
                pcpManager = new PcpManager(gateway.get(), localAddr);
                pcpManager.start();

                PcpManager.MappingResult pcpResult = pcpManager
                        .mapPort(PcpManager.Protocol.TCP, port, port, lifetime)
                        .join();

                if (pcpResult.success()) {
                    activeMethod.set(MappingMethod.PCP);
                    mappedPort = pcpResult.externalPort();
                    externalAddress = pcpResult.externalAddress();
                    return new MappingResult(true, pcpResult.externalAddress(),
                            pcpResult.externalPort(), null);
                }
                return new MappingResult(false, null, 0, pcpResult.error());
            } catch (IOException e) {
                return new MappingResult(false, null, 0, e.getMessage());
            }
        });
    }

    private CompletableFuture<MappingResult> tryUpnp(Duration lifetime) {
        return CompletableFuture.completedFuture(
                new MappingResult(false, null, 0, "UPnP fallback not in this class"));
    }

    public Optional<MappingMethod> getActiveMethod() {
        return Optional.ofNullable(activeMethod.get());
    }

    public Optional<InetAddress> getExternalAddress() {
        return Optional.ofNullable(externalAddress);
    }

    public int getMappedPort() {
        return mappedPort;
    }

    public CompletableFuture<Void> unmap() {
        MappingMethod method = activeMethod.get();
        if (method == null) {
            return CompletableFuture.completedFuture(null);
        }

        return switch (method) {
            case NAT_PMP -> natPmpManager != null
                    ? natPmpManager.unmapPort(NatPmpManager.Protocol.TCP, port)
                            .thenAccept(v -> {
                            })
                    : CompletableFuture.completedFuture(null);
            case PCP -> pcpManager != null
                    ? pcpManager.unmapPort(PcpManager.Protocol.TCP, port)
                            .thenAccept(v -> {
                            })
                    : CompletableFuture.completedFuture(null);
            case UPNP -> CompletableFuture.completedFuture(null);
        };
    }

    @Override
    public void close() {
        unmap().join();
        if (natPmpManager != null) {
            natPmpManager.close();
        }
        if (pcpManager != null) {
            pcpManager.close();
        }
    }

    public enum MappingMethod {
        NAT_PMP, PCP, UPNP
    }

    public record MappingResult(boolean success, InetAddress externalAddress,
            int externalPort, String error) {
    }
}
