package com.example.jtorrent.web;

import com.example.jtorrent.config.Config;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for WebServer.
 */
@DisplayName("WebServer Tests")
class WebServerTest {

    private Config config;
    private WebServer webServer;
    private RestRouter router;
    private AuthManager authManager;

    @BeforeEach
    void setUp() {
        config = mock(Config.class);
        when(config.getWebPort()).thenReturn(0); // Random port
        when(config.getWebSocketPort()).thenReturn(0);

        router = mock(RestRouter.class);
        authManager = mock(AuthManager.class);
    }

    @AfterEach
    void tearDown() {
        if (webServer != null) {
            webServer.stop();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create web server with config")
        void shouldCreateWebServerWithConfig() {
            webServer = new WebServer(config, router, authManager);
            assertNotNull(webServer);
        }
    }

    @Nested
    @DisplayName("Start/Stop Tests")
    class StartStopTests {

        @Test
        @DisplayName("Should start server")
        void shouldStartServer() throws IOException {
            when(config.getWebPort()).thenReturn(18080);
            webServer = new WebServer(config, router, authManager);

            webServer.start();

            assertTrue(webServer.isRunning());
        }

        @Test
        @DisplayName("Should stop server")
        void shouldStopServer() throws IOException {
            when(config.getWebPort()).thenReturn(18081);
            webServer = new WebServer(config, router, authManager);

            webServer.start();
            webServer.stop();

            assertFalse(webServer.isRunning());
        }

        @Test
        @DisplayName("Should handle multiple starts")
        void shouldHandleMultipleStarts() throws IOException {
            when(config.getWebPort()).thenReturn(18082);
            webServer = new WebServer(config, router, authManager);

            webServer.start();
            webServer.start(); // Should not throw

            assertTrue(webServer.isRunning());
        }

        @Test
        @DisplayName("Should handle multiple stops")
        void shouldHandleMultipleStops() throws IOException {
            when(config.getWebPort()).thenReturn(18083);
            webServer = new WebServer(config, router, authManager);

            webServer.start();
            webServer.stop();
            webServer.stop(); // Should not throw

            assertFalse(webServer.isRunning());
        }
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should reject unauthenticated requests")
        void shouldRejectUnauthenticatedRequests() throws Exception {
            when(config.getWebPort()).thenReturn(18084);
            when(authManager.authenticate(null)).thenReturn(false);

            webServer = new WebServer(config, router, authManager);
            webServer.start();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:18084/api/torrents"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode());
        }

        @Test
        @DisplayName("Should accept authenticated requests")
        void shouldAcceptAuthenticatedRequests() throws Exception {
            when(config.getWebPort()).thenReturn(18085);
            when(authManager.authenticate(anyString())).thenReturn(true);
            when(router.route(anyString(), anyString(), any())).thenReturn("{}");

            webServer = new WebServer(config, router, authManager);
            webServer.start();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:18085/api/torrents"))
                    .header("Authorization", "Bearer test-token")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
        }
    }

    @Nested
    @DisplayName("Stats Publisher Tests")
    class StatsPublisherTests {

        @Test
        @DisplayName("Should return stats publisher")
        void shouldReturnStatsPublisher() throws IOException {
            when(config.getWebPort()).thenReturn(18086);
            webServer = new WebServer(config, router, authManager);

            assertNotNull(webServer.getStatsPublisher());
        }
    }

    @Nested
    @DisplayName("Routing Tests")
    class RoutingTests {

        @Test
        @DisplayName("Should route torrent requests")
        void shouldRouteTorrentRequests() throws Exception {
            when(config.getWebPort()).thenReturn(18087);
            when(authManager.authenticate(anyString())).thenReturn(true);
            when(router.route(eq("GET"), contains("/api/torrents"), any()))
                    .thenReturn("{\"torrents\":[]}");

            webServer = new WebServer(config, router, authManager);
            webServer.start();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:18087/api/torrents"))
                    .header("Authorization", "Bearer token")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("torrents"));
        }

        @Test
        @DisplayName("Should route stats requests")
        void shouldRouteStatsRequests() throws Exception {
            when(config.getWebPort()).thenReturn(18088);
            when(authManager.authenticate(anyString())).thenReturn(true);
            when(router.route(eq("GET"), contains("/api/stats"), any()))
                    .thenReturn("{\"downloadSpeed\":0}");

            webServer = new WebServer(config, router, authManager);
            webServer.start();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:18088/api/stats"))
                    .header("Authorization", "Bearer token")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle router exceptions")
        void shouldHandleRouterExceptions() throws Exception {
            when(config.getWebPort()).thenReturn(18089);
            when(authManager.authenticate(anyString())).thenReturn(true);
            when(router.route(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            webServer = new WebServer(config, router, authManager);
            webServer.start();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:18089/api/torrents"))
                    .header("Authorization", "Bearer token")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(500, response.statusCode());
        }
    }
}
