package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UPnP port mapping manager for automatic NAT traversal.
 * 
 * Implements UPnP IGD (Internet Gateway Device) protocol to automatically
 * configure port forwarding on the router for incoming peer connections.
 */
public class UPnPManager {

    private static final Logger logger = Logger.getLogger(UPnPManager.class);

    private static final int SSDP_PORT = 1900;
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int DISCOVERY_TIMEOUT_MS = 3000;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicInteger mappedPort = new AtomicInteger(0);

    private String gatewayLocation;
    private String controlUrl;
    private String serviceType;
    private InetAddress localAddress;

    /**
     * Discover UPnP gateway on the network.
     * 
     * @return true if gateway found
     */
    public boolean discoverGateway() {
        if (initialized.get()) {
            return true;
        }

        logger.info("Discovering UPnP gateway...");

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);
            socket.setBroadcast(true);

            // SSDP M-SEARCH request
            String searchRequest = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
                    "\r\n";

            byte[] requestBytes = searchRequest.getBytes(StandardCharsets.UTF_8);
            InetAddress ssdpAddr = InetAddress.getByName(SSDP_ADDRESS);
            DatagramPacket request = new DatagramPacket(
                    requestBytes, requestBytes.length, ssdpAddr, SSDP_PORT);

            socket.send(request);

            // Receive response
            byte[] responseBuffer = new byte[2048];
            DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);

            try {
                socket.receive(response);
                String responseStr = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);

                // Parse LOCATION header
                for (String line : responseStr.split("\r\n")) {
                    if (line.toLowerCase().startsWith("location:")) {
                        gatewayLocation = line.substring(9).trim();
                        break;
                    }
                }

                if (gatewayLocation != null) {
                    logger.info("Found UPnP gateway at: %s", gatewayLocation);
                    localAddress = socket.getLocalAddress();

                    // Get control URL
                    if (fetchControlUrl()) {
                        initialized.set(true);
                        return true;
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                logger.warn("UPnP discovery timed out - no gateway found");
            }

        } catch (IOException e) {
            logger.warn("UPnP discovery failed: %s", e.getMessage());
        }

        return false;
    }

    /**
     * Fetch the control URL from the gateway description.
     */
    private boolean fetchControlUrl() {
        if (gatewayLocation == null)
            return false;

        try {
            URL url = new URL(gatewayLocation);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String xml = response.toString();

            // Look for WANIPConnection or WANPPPConnection service
            String[] serviceTypes = {
                    "urn:schemas-upnp-org:service:WANIPConnection:1",
                    "urn:schemas-upnp-org:service:WANPPPConnection:1"
            };

            for (String st : serviceTypes) {
                int idx = xml.indexOf(st);
                if (idx >= 0) {
                    serviceType = st;
                    // Find controlURL for this service
                    int ctrlIdx = xml.indexOf("<controlURL>", idx);
                    if (ctrlIdx >= 0) {
                        int endIdx = xml.indexOf("</controlURL>", ctrlIdx);
                        if (endIdx > ctrlIdx) {
                            String ctrlPath = xml.substring(ctrlIdx + 12, endIdx);

                            // Build full control URL
                            URL base = new URL(gatewayLocation);
                            if (ctrlPath.startsWith("/")) {
                                controlUrl = base.getProtocol() + "://" + base.getHost() +
                                        (base.getPort() > 0 ? ":" + base.getPort() : "") + ctrlPath;
                            } else {
                                controlUrl = gatewayLocation.substring(0, gatewayLocation.lastIndexOf('/') + 1)
                                        + ctrlPath;
                            }

                            logger.info("Found UPnP control URL: %s", controlUrl);
                            return true;
                        }
                    }
                }
            }

            logger.warn("Could not find WANIPConnection service in gateway description");

        } catch (IOException e) {
            logger.warn("Failed to fetch gateway description: %s", e.getMessage());
        }

        return false;
    }

    /**
     * Add a port mapping.
     * 
     * @param internalPort  the local port to map
     * @param externalPort  the external port to request (0 for same as internal)
     * @param protocol      "TCP" or "UDP"
     * @param description   description for the mapping
     * @param leaseDuration lease duration in seconds (0 for permanent)
     * @return the mapped external port, or 0 if failed
     */
    public int addPortMapping(int internalPort, int externalPort, String protocol,
            String description, int leaseDuration) {
        if (!initialized.get()) {
            if (!discoverGateway()) {
                return 0;
            }
        }

        if (externalPort == 0) {
            externalPort = internalPort;
        }

        logger.info("Adding UPnP port mapping: %d -> %d (%s)", externalPort, internalPort, protocol);

        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();

            String soapBody = "<?xml version=\"1.0\"?>\r\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                    "<s:Body>\r\n" +
                    "<u:AddPortMapping xmlns:u=\"" + serviceType + "\">\r\n" +
                    "<NewRemoteHost></NewRemoteHost>\r\n" +
                    "<NewExternalPort>" + externalPort + "</NewExternalPort>\r\n" +
                    "<NewProtocol>" + protocol + "</NewProtocol>\r\n" +
                    "<NewInternalPort>" + internalPort + "</NewInternalPort>\r\n" +
                    "<NewInternalClient>" + localIp + "</NewInternalClient>\r\n" +
                    "<NewEnabled>1</NewEnabled>\r\n" +
                    "<NewPortMappingDescription>" + description + "</NewPortMappingDescription>\r\n" +
                    "<NewLeaseDuration>" + leaseDuration + "</NewLeaseDuration>\r\n" +
                    "</u:AddPortMapping>\r\n" +
                    "</s:Body>\r\n" +
                    "</s:Envelope>";

            int responseCode = sendSoapRequest("AddPortMapping", soapBody);

            if (responseCode == 200) {
                mappedPort.set(externalPort);
                logger.info("UPnP port mapping added successfully: external port %d", externalPort);
                return externalPort;
            } else {
                logger.warn("UPnP AddPortMapping failed with code: %d", responseCode);
            }

        } catch (IOException e) {
            logger.warn("Failed to add port mapping: %s", e.getMessage());
        }

        return 0;
    }

    /**
     * Delete a port mapping.
     * 
     * @param externalPort the external port to unmap
     * @param protocol     "TCP" or "UDP"
     * @return true if successful
     */
    public boolean deletePortMapping(int externalPort, String protocol) {
        if (!initialized.get()) {
            return false;
        }

        logger.info("Deleting UPnP port mapping: %d (%s)", externalPort, protocol);

        try {
            String soapBody = "<?xml version=\"1.0\"?>\r\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                    "<s:Body>\r\n" +
                    "<u:DeletePortMapping xmlns:u=\"" + serviceType + "\">\r\n" +
                    "<NewRemoteHost></NewRemoteHost>\r\n" +
                    "<NewExternalPort>" + externalPort + "</NewExternalPort>\r\n" +
                    "<NewProtocol>" + protocol + "</NewProtocol>\r\n" +
                    "</u:DeletePortMapping>\r\n" +
                    "</s:Body>\r\n" +
                    "</s:Envelope>";

            int responseCode = sendSoapRequest("DeletePortMapping", soapBody);

            if (responseCode == 200) {
                if (mappedPort.get() == externalPort) {
                    mappedPort.set(0);
                }
                logger.info("UPnP port mapping deleted successfully");
                return true;
            }

        } catch (IOException e) {
            logger.warn("Failed to delete port mapping: %s", e.getMessage());
        }

        return false;
    }

    /**
     * Get the external IP address from the gateway.
     * 
     * @return external IP address or null if unavailable
     */
    public String getExternalIPAddress() {
        if (!initialized.get()) {
            if (!discoverGateway()) {
                return null;
            }
        }

        try {
            String soapBody = "<?xml version=\"1.0\"?>\r\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                    "<s:Body>\r\n" +
                    "<u:GetExternalIPAddress xmlns:u=\"" + serviceType + "\">\r\n" +
                    "</u:GetExternalIPAddress>\r\n" +
                    "</s:Body>\r\n" +
                    "</s:Envelope>";

            URL url = new URL(controlUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "\"" + serviceType + "#GetExternalIPAddress\"");

            conn.getOutputStream().write(soapBody.getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String xml = response.toString();
                int start = xml.indexOf("<NewExternalIPAddress>");
                if (start >= 0) {
                    int end = xml.indexOf("</NewExternalIPAddress>", start);
                    if (end > start) {
                        return xml.substring(start + 22, end);
                    }
                }
            }

        } catch (IOException e) {
            logger.warn("Failed to get external IP: %s", e.getMessage());
        }

        return null;
    }

    /**
     * Send a SOAP request to the gateway.
     */
    private int sendSoapRequest(String action, String body) throws IOException {
        URL url = new URL(controlUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + action + "\"");

        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        return conn.getResponseCode();
    }

    /**
     * Get the currently mapped port.
     * 
     * @return mapped port or 0 if none
     */
    public int getMappedPort() {
        return mappedPort.get();
    }

    /**
     * Check if UPnP is available.
     * 
     * @return true if gateway discovered
     */
    public boolean isAvailable() {
        return initialized.get();
    }

    /**
     * Clean up and remove port mappings.
     */
    public void cleanup() {
        int port = mappedPort.get();
        if (port > 0) {
            deletePortMapping(port, "TCP");
            deletePortMapping(port, "UDP");
        }
        initialized.set(false);
    }
}
