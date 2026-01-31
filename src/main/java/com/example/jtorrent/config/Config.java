package com.example.jtorrent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration manager for JTorrent.
 * Supports JSON configuration files and command-line overrides.
 */
public class Config {

  private static final int DEFAULT_PORT = 6881;
  private static final int DEFAULT_MAX_PEERS = 50;
  private static final int DEFAULT_UPLOAD_SLOTS = 4;
  private static final long DEFAULT_MAX_UPLOAD_BPS = 0; // unlimited
  private static final long DEFAULT_MAX_DOWNLOAD_BPS = 0; // unlimited
  private static final String DEFAULT_DOWNLOAD_DIR = "./downloads";
  private static final String DEFAULT_STATE_DIR = "./state";
  private static final boolean DEFAULT_DHT_ENABLED = true;
  private static final boolean DEFAULT_PEX_ENABLED = true;
  private static final int DEFAULT_DHT_PORT = 6881;
  private static final int DEFAULT_METADATA_CONNECT_TIMEOUT_MS = 15000;
  private static final int DEFAULT_METADATA_CONNECT_RETRIES = 3;
  private static final int DEFAULT_METADATA_PARALLEL_PEERS = 20;
  private static final int DEFAULT_METADATA_PIECE_TIMEOUT_MS = 10000;

  // Network settings
  private int port = DEFAULT_PORT;
  private int maxPeers = DEFAULT_MAX_PEERS;
  private String listenAddress = "0.0.0.0";

  // Transfer settings
  private int uploadSlots = DEFAULT_UPLOAD_SLOTS;
  private long maxUploadBytesPerSec = DEFAULT_MAX_UPLOAD_BPS;
  private long maxDownloadBytesPerSec = DEFAULT_MAX_DOWNLOAD_BPS;

  // Path settings
  private String downloadDirectory = DEFAULT_DOWNLOAD_DIR;
  private String stateDirectory = DEFAULT_STATE_DIR;

  // Feature toggles
  private boolean dhtEnabled = DEFAULT_DHT_ENABLED;
  private boolean pexEnabled = DEFAULT_PEX_ENABLED;
  private int dhtPort = DEFAULT_DHT_PORT;
  private List<String> dhtBootstrapNodes = getDefaultBootstrapNodes();

  // Logging settings
  private String logLevel = "INFO";
  private String logFile = null;

  // Advanced settings
  private int pieceTimeout = 30000; // 30 seconds
  private int requestQueueSize = 5;
  private boolean encryptionEnabled = false;
  private List<String> trackerBlacklist = new ArrayList<>();

  // Metadata fetching settings (for magnet links)
  private int metadataConnectTimeoutMs = DEFAULT_METADATA_CONNECT_TIMEOUT_MS;
  private int metadataConnectRetries = DEFAULT_METADATA_CONNECT_RETRIES;
  private int metadataParallelPeers = DEFAULT_METADATA_PARALLEL_PEERS;
  private int metadataPieceTimeoutMs = DEFAULT_METADATA_PIECE_TIMEOUT_MS;

  // Web server settings
  private int webPort = 8080;
  private int webSocketPort = 8081;
  private String webPassword = "admin";

  public static Config fromFile(Path configPath) throws IOException {
    String content = Files.readString(configPath);
    return fromJson(content);
  }

  @SuppressWarnings("unchecked")
  public static Config fromJson(String json) {
    Config config = new Config();

    try {
      // Simple JSON parsing (would use a library in production)
      Map<String, Object> map = parseSimpleJson(json);

      if (map.containsKey("port")) {
        config.port = ((Number) map.get("port")).intValue();
      }
      if (map.containsKey("maxPeers")) {
        config.maxPeers = ((Number) map.get("maxPeers")).intValue();
      }
      if (map.containsKey("listenAddress")) {
        config.listenAddress = (String) map.get("listenAddress");
      }
      if (map.containsKey("uploadSlots")) {
        config.uploadSlots = ((Number) map.get("uploadSlots")).intValue();
      }
      if (map.containsKey("maxUploadKBps")) {
        config.maxUploadBytesPerSec = ((Number) map.get("maxUploadKBps")).longValue() * 1024;
      }
      if (map.containsKey("maxDownloadKBps")) {
        config.maxDownloadBytesPerSec = ((Number) map.get("maxDownloadKBps")).longValue() * 1024;
      }
      if (map.containsKey("downloadDirectory")) {
        config.downloadDirectory = (String) map.get("downloadDirectory");
      }
      if (map.containsKey("stateDirectory")) {
        config.stateDirectory = (String) map.get("stateDirectory");
      }
      if (map.containsKey("dhtEnabled")) {
        config.dhtEnabled = (Boolean) map.get("dhtEnabled");
      }
      if (map.containsKey("pexEnabled")) {
        config.pexEnabled = (Boolean) map.get("pexEnabled");
      }
      if (map.containsKey("logLevel")) {
        config.logLevel = (String) map.get("logLevel");
      }
      if (map.containsKey("logFile")) {
        config.logFile = (String) map.get("logFile");
      }
      if (map.containsKey("metadataConnectTimeoutMs")) {
        config.metadataConnectTimeoutMs = ((Number) map.get("metadataConnectTimeoutMs")).intValue();
      }
      if (map.containsKey("metadataConnectRetries")) {
        config.metadataConnectRetries = ((Number) map.get("metadataConnectRetries")).intValue();
      }
      if (map.containsKey("metadataParallelPeers")) {
        config.metadataParallelPeers = ((Number) map.get("metadataParallelPeers")).intValue();
      }
      if (map.containsKey("metadataPieceTimeoutMs")) {
        config.metadataPieceTimeoutMs = ((Number) map.get("metadataPieceTimeoutMs")).intValue();
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON configuration: " + e.getMessage(), e);
    }

    return config;
  }

  /** Simple JSON parser for configuration (use proper library in production). */
  private static Map<String, Object> parseSimpleJson(String json) {
    Map<String, Object> result = new HashMap<>();
    json = json.trim();

    if (!json.startsWith("{") || !json.endsWith("}")) {
      return result;
    }

    json = json.substring(1, json.length() - 1).trim();
    String[] pairs = json.split(",");

    for (String pair : pairs) {
      String[] kv = pair.split(":", 2);
      if (kv.length != 2) {
        continue;
      }

      String key = kv[0].trim().replace("\"", "");
      String value = kv[1].trim();

      // Parse value
      Object parsedValue;
      if (value.equals("true")) {
        parsedValue = true;
      } else if (value.equals("false")) {
        parsedValue = false;
      } else if (value.startsWith("\"") && value.endsWith("\"")) {
        parsedValue = value.substring(1, value.length() - 1);
      } else {
        try {
          if (value.contains(".")) {
            parsedValue = Double.parseDouble(value);
          } else {
            parsedValue = Long.parseLong(value);
          }
        } catch (NumberFormatException e) {
          parsedValue = value;
        }
      }

      result.put(key, parsedValue);
    }

    return result;
  }

  /**
   * Export configuration to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"port\": ").append(port).append(",\n");
    sb.append("  \"maxPeers\": ").append(maxPeers).append(",\n");
    sb.append("  \"listenAddress\": \"").append(listenAddress).append("\",\n");
    sb.append("  \"uploadSlots\": ").append(uploadSlots).append(",\n");
    sb.append("  \"maxUploadKBps\": ").append(maxUploadBytesPerSec / 1024).append(",\n");
    sb.append("  \"maxDownloadKBps\": ").append(maxDownloadBytesPerSec / 1024).append(",\n");
    sb.append("  \"downloadDirectory\": \"").append(downloadDirectory).append("\",\n");
    sb.append("  \"stateDirectory\": \"").append(stateDirectory).append("\",\n");
    sb.append("  \"dhtEnabled\": ").append(dhtEnabled).append(",\n");
    sb.append("  \"pexEnabled\": ").append(pexEnabled).append(",\n");
    sb.append("  \"logLevel\": \"").append(logLevel).append("\",\n");
    if (logFile != null) {
      sb.append("  \"logFile\": \"").append(logFile).append("\",\n");
    }
    sb.append("  \"encryptionEnabled\": ").append(encryptionEnabled).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Save configuration to file.
   *
   * @param configPath path to save to
   * @throws IOException if file cannot be written
   */
  public void save(Path configPath) throws IOException {
    Files.writeString(configPath, toJson());
  }

  /**
   * Get default DHT bootstrap nodes.
   *
   * @return list of bootstrap nodes
   */
  private static List<String> getDefaultBootstrapNodes() {
    List<String> nodes = new ArrayList<>();
    nodes.add("router.bittorrent.com:6881");
    nodes.add("dht.transmissionbt.com:6881");
    nodes.add("router.utorrent.com:6881");
    return nodes;
  }

  // Getters and setters

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getMaxPeers() {
    return maxPeers;
  }

  public void setMaxPeers(int maxPeers) {
    this.maxPeers = maxPeers;
  }

  public String getListenAddress() {
    return listenAddress;
  }

  public void setListenAddress(String listenAddress) {
    this.listenAddress = listenAddress;
  }

  public int getUploadSlots() {
    return uploadSlots;
  }

  public void setUploadSlots(int uploadSlots) {
    this.uploadSlots = uploadSlots;
  }

  public long getMaxUploadBytesPerSec() {
    return maxUploadBytesPerSec;
  }

  public void setMaxUploadBytesPerSec(long maxUploadBytesPerSec) {
    this.maxUploadBytesPerSec = maxUploadBytesPerSec;
  }

  public long getMaxDownloadBytesPerSec() {
    return maxDownloadBytesPerSec;
  }

  public void setMaxDownloadBytesPerSec(long maxDownloadBytesPerSec) {
    this.maxDownloadBytesPerSec = maxDownloadBytesPerSec;
  }

  public String getDownloadDirectory() {
    return downloadDirectory;
  }

  public void setDownloadDirectory(String downloadDirectory) {
    this.downloadDirectory = downloadDirectory;
  }

  public String getStateDirectory() {
    return stateDirectory;
  }

  public void setStateDirectory(String stateDirectory) {
    this.stateDirectory = stateDirectory;
  }

  public boolean isDhtEnabled() {
    return dhtEnabled;
  }

  public void setDhtEnabled(boolean dhtEnabled) {
    this.dhtEnabled = dhtEnabled;
  }

  public boolean isPexEnabled() {
    return pexEnabled;
  }

  public void setPexEnabled(boolean pexEnabled) {
    this.pexEnabled = pexEnabled;
  }

  public int getDhtPort() {
    return dhtPort;
  }

  public void setDhtPort(int dhtPort) {
    this.dhtPort = dhtPort;
  }

  public List<String> getDhtBootstrapNodes() {
    return new ArrayList<>(dhtBootstrapNodes);
  }

  public void setDhtBootstrapNodes(List<String> dhtBootstrapNodes) {
    this.dhtBootstrapNodes = new ArrayList<>(dhtBootstrapNodes);
  }

  public String getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(String logLevel) {
    this.logLevel = logLevel;
  }

  public String getLogFile() {
    return logFile;
  }

  public void setLogFile(String logFile) {
    this.logFile = logFile;
  }

  public int getPieceTimeout() {
    return pieceTimeout;
  }

  public void setPieceTimeout(int pieceTimeout) {
    this.pieceTimeout = pieceTimeout;
  }

  public int getRequestQueueSize() {
    return requestQueueSize;
  }

  public void setRequestQueueSize(int requestQueueSize) {
    this.requestQueueSize = requestQueueSize;
  }

  public boolean isEncryptionEnabled() {
    return encryptionEnabled;
  }

  public void setEncryptionEnabled(boolean encryptionEnabled) {
    this.encryptionEnabled = encryptionEnabled;
  }

  public List<String> getTrackerBlacklist() {
    return new ArrayList<>(trackerBlacklist);
  }

  public void setTrackerBlacklist(List<String> trackerBlacklist) {
    this.trackerBlacklist = new ArrayList<>(trackerBlacklist);
  }

  public int getMetadataConnectTimeoutMs() {
    return metadataConnectTimeoutMs;
  }

  public void setMetadataConnectTimeoutMs(int metadataConnectTimeoutMs) {
    this.metadataConnectTimeoutMs = metadataConnectTimeoutMs;
  }

  public int getMetadataConnectRetries() {
    return metadataConnectRetries;
  }

  public void setMetadataConnectRetries(int metadataConnectRetries) {
    this.metadataConnectRetries = metadataConnectRetries;
  }

  public int getMetadataParallelPeers() {
    return metadataParallelPeers;
  }

  public void setMetadataParallelPeers(int metadataParallelPeers) {
    this.metadataParallelPeers = metadataParallelPeers;
  }

  public int getMetadataPieceTimeoutMs() {
    return metadataPieceTimeoutMs;
  }

  public void setMetadataPieceTimeoutMs(int metadataPieceTimeoutMs) {
    this.metadataPieceTimeoutMs = metadataPieceTimeoutMs;
  }

  public int getWebPort() {
    return webPort;
  }

  public void setWebPort(int webPort) {
    this.webPort = webPort;
  }

  public int getWebSocketPort() {
    return webSocketPort;
  }

  public void setWebSocketPort(int webSocketPort) {
    this.webSocketPort = webSocketPort;
  }

  public String getWebPassword() {
    return webPassword;
  }

  public void setWebPassword(String webPassword) {
    this.webPassword = webPassword;
  }

  /**
   * Create a copy of this configuration.
   *
   * @return a new Config object with the same values
   */
  public Config copy() {
    Config copy = new Config();
    copy.port = this.port;
    copy.maxPeers = this.maxPeers;
    copy.listenAddress = this.listenAddress;
    copy.uploadSlots = this.uploadSlots;
    copy.maxUploadBytesPerSec = this.maxUploadBytesPerSec;
    copy.maxDownloadBytesPerSec = this.maxDownloadBytesPerSec;
    copy.downloadDirectory = this.downloadDirectory;
    copy.stateDirectory = this.stateDirectory;
    copy.dhtEnabled = this.dhtEnabled;
    copy.pexEnabled = this.pexEnabled;
    copy.dhtPort = this.dhtPort;
    copy.dhtBootstrapNodes = new ArrayList<>(this.dhtBootstrapNodes);
    copy.logLevel = this.logLevel;
    copy.logFile = this.logFile;
    copy.pieceTimeout = this.pieceTimeout;
    copy.requestQueueSize = this.requestQueueSize;
    copy.encryptionEnabled = this.encryptionEnabled;
    copy.trackerBlacklist = new ArrayList<>(this.trackerBlacklist);
    copy.metadataConnectTimeoutMs = this.metadataConnectTimeoutMs;
    copy.metadataConnectRetries = this.metadataConnectRetries;
    copy.metadataParallelPeers = this.metadataParallelPeers;
    copy.metadataPieceTimeoutMs = this.metadataPieceTimeoutMs;
    return copy;
  }

  /**
   * Copy values from another Config object.
   *
   * @param other the Config to copy from
   */
  public void copyFrom(Config other) {
    this.port = other.port;
    this.maxPeers = other.maxPeers;
    this.listenAddress = other.listenAddress;
    this.uploadSlots = other.uploadSlots;
    this.maxUploadBytesPerSec = other.maxUploadBytesPerSec;
    this.maxDownloadBytesPerSec = other.maxDownloadBytesPerSec;
    this.downloadDirectory = other.downloadDirectory;
    this.stateDirectory = other.stateDirectory;
    this.dhtEnabled = other.dhtEnabled;
    this.pexEnabled = other.pexEnabled;
    this.dhtPort = other.dhtPort;
    this.dhtBootstrapNodes = new ArrayList<>(other.dhtBootstrapNodes);
    this.logLevel = other.logLevel;
    this.logFile = other.logFile;
    this.pieceTimeout = other.pieceTimeout;
    this.requestQueueSize = other.requestQueueSize;
    this.encryptionEnabled = other.encryptionEnabled;
    this.trackerBlacklist = new ArrayList<>(other.trackerBlacklist);
    this.metadataConnectTimeoutMs = other.metadataConnectTimeoutMs;
    this.metadataConnectRetries = other.metadataConnectRetries;
    this.metadataParallelPeers = other.metadataParallelPeers;
    this.metadataPieceTimeoutMs = other.metadataPieceTimeoutMs;
  }

  @Override
  public String toString() {
    return String.format("Config[port=%d, maxPeers=%d, uploadSlots=%d, "
        + "maxUpload=%d KB/s, maxDownload=%d KB/s, dht=%b, pex=%b]",
        port, maxPeers, uploadSlots,
        maxUploadBytesPerSec / 1024, maxDownloadBytesPerSec / 1024,
        dhtEnabled, pexEnabled);
  }
}
