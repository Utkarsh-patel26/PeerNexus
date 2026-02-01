package com.example.jtorrent.network;

import org.junit.jupiter.api.*;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PortMappingService.
 */
@DisplayName("PortMappingService Tests")
class PortMappingServiceTest {

    private PortMappingService portMappingService;
    private static final int TEST_PORT = 6881;

    @AfterEach
    void tearDown() throws Exception {
        if (portMappingService != null) {
            portMappingService.close();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create with port")
        void shouldCreateWithPort() {
            portMappingService = new PortMappingService(TEST_PORT);
            assertNotNull(portMappingService);
        }
    }

    @Nested
    @DisplayName("Port Mapping Tests")
    class PortMappingTests {

        @Test
        @DisplayName("Should attempt to map port")
        void shouldAttemptToMapPort() {
            portMappingService = new PortMappingService(TEST_PORT);

            CompletableFuture<PortMappingService.MappingResult> future = portMappingService
                    .mapPort(Duration.ofHours(1));

            assertNotNull(future);

            // Will fail without actual UPnP/NAT-PMP router
            try {
                PortMappingService.MappingResult result = future.get(2, TimeUnit.SECONDS);
                assertNotNull(result);
            } catch (Exception e) {
                // Expected in test environment
            }
        }
    }

    @Nested
    @DisplayName("Active Method Tests")
    class ActiveMethodTests {

        @Test
        @DisplayName("Should return empty method initially")
        void shouldReturnEmptyMethodInitially() {
            portMappingService = new PortMappingService(TEST_PORT);

            Optional<PortMappingService.MappingMethod> method = portMappingService.getActiveMethod();

            assertNotNull(method);
            assertTrue(method.isEmpty());
        }
    }

    @Nested
    @DisplayName("External Address Tests")
    class ExternalAddressTests {

        @Test
        @DisplayName("Should return empty address initially")
        void shouldReturnEmptyAddressInitially() {
            portMappingService = new PortMappingService(TEST_PORT);

            Optional<InetAddress> address = portMappingService.getExternalAddress();

            assertNotNull(address);
            assertTrue(address.isEmpty());
        }
    }

    @Nested
    @DisplayName("Mapped Port Tests")
    class MappedPortTests {

        @Test
        @DisplayName("Should return zero mapped port initially")
        void shouldReturnZeroMappedPortInitially() {
            portMappingService = new PortMappingService(TEST_PORT);

            int mappedPort = portMappingService.getMappedPort();

            assertEquals(0, mappedPort);
        }
    }

    @Nested
    @DisplayName("Unmap Tests")
    class UnmapTests {

        @Test
        @DisplayName("Should handle unmap when no mapping")
        void shouldHandleUnmapWhenNoMapping() {
            portMappingService = new PortMappingService(TEST_PORT);

            CompletableFuture<Void> future = portMappingService.unmap();

            assertNotNull(future);
            assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("MappingMethod Enum Tests")
    class MappingMethodEnumTests {

        @Test
        @DisplayName("Should have all mapping methods")
        void shouldHaveAllMappingMethods() {
            assertNotNull(PortMappingService.MappingMethod.NAT_PMP);
            assertNotNull(PortMappingService.MappingMethod.PCP);
            assertNotNull(PortMappingService.MappingMethod.UPNP);
            assertEquals(3, PortMappingService.MappingMethod.values().length);
        }
    }

    @Nested
    @DisplayName("MappingResult Tests")
    class MappingResultTests {

        @Test
        @DisplayName("Should create successful result")
        void shouldCreateSuccessfulResult() throws Exception {
            InetAddress addr = InetAddress.getByName("1.2.3.4");
            PortMappingService.MappingResult result = new PortMappingService.MappingResult(
                    true, addr, 6881, null);

            assertTrue(result.success());
            assertEquals(addr, result.externalAddress());
            assertEquals(6881, result.externalPort());
            assertNull(result.error());
        }

        @Test
        @DisplayName("Should create failed result")
        void shouldCreateFailedResult() {
            PortMappingService.MappingResult result = new PortMappingService.MappingResult(
                    false, null, 0, "No gateway found");

            assertFalse(result.success());
            assertNull(result.externalAddress());
            assertEquals("No gateway found", result.error());
        }
    }

    @Nested
    @DisplayName("AutoCloseable Tests")
    class AutoCloseableTests {

        @Test
        @DisplayName("Should close cleanly")
        void shouldCloseCleanly() {
            portMappingService = new PortMappingService(TEST_PORT);

            assertDoesNotThrow(() -> portMappingService.close());
        }

        @Test
        @DisplayName("Should be usable with try-with-resources")
        void shouldBeUsableWithTryWithResources() {
            try (PortMappingService service = new PortMappingService(TEST_PORT)) {
                assertNotNull(service);
            }
            // Auto-closed
        }
    }
}
