package com.example.jtorrent.gui;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.gui.components.DownloadItem;
import com.example.jtorrent.gui.components.DownloadManagerPanel;
import com.example.jtorrent.gui.components.FileListPanel;
import com.example.jtorrent.gui.components.MenuBarController;
import com.example.jtorrent.gui.components.PeerListPanel;
import com.example.jtorrent.gui.components.PieceMapPanel;
import com.example.jtorrent.gui.components.StatisticsPanel;
import com.example.jtorrent.gui.components.TrackerListPanel;
import com.example.jtorrent.logging.Logger;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Main window controller for JTorrent GUI.
 * Modern, clean UI with Discord/Steam-inspired design.
 */
public class MainWindowController {

  private static final Logger logger = Logger.getLogger(MainWindowController.class);

  private Config config;
  private MenuBarController menuBarController;
  private DownloadManagerPanel downloadManagerPanel;
  private StatisticsPanel statisticsPanel;
  private FileListPanel fileListPanel;
  private TrackerListPanel trackerListPanel;
  private PeerListPanel peerListPanel;
  private PieceMapPanel pieceMapPanel;
  private SplitPane mainSplitPane;
  private TabPane detailsTabPane;
  private HBox statusBar;
  private DownloadItem selectedDownloadItem;

  // Status bar labels
  private Label downloadingCountLabel;
  private Label seedingCountLabel;
  private Label downloadSpeedLabel;
  private Label uploadSpeedLabel;
  private Label peersCountLabel;

  // Stats tracking
  private int downloadingCount = 0;
  private int seedingCount = 0;
  private long totalDownloadSpeed = 0;
  private long totalUploadSpeed = 0;
  private int totalPeers = 0;

  /**
   * Constructor - initializes configuration.
   */
  public MainWindowController() {
    loadConfiguration();
  }

  /**
   * Load configuration from file or use defaults.
   */
  private void loadConfiguration() {
    try {
      Path configPath = Paths.get("config.json");
      if (configPath.toFile().exists()) {
        config = Config.fromFile(configPath);
        logger.info("Loaded configuration from config.json");
      } else {
        config = new Config();
        logger.info("Using default configuration");
      }
    } catch (Exception e) {
      logger.error("Failed to load configuration, using defaults: " + e.getMessage());
      config = new Config();
    }
  }

  /**
   * Create the main layout for the application.
   *
   * @return the root BorderPane
   */
  public BorderPane createMainLayout() {
    BorderPane root = new BorderPane();
    root.getStyleClass().add("root");

    // Top: Menu bar only (toolbar is in DownloadManagerPanel)
    menuBarController = new MenuBarController(this);
    MenuBar menuBar = menuBarController.createMenuBar();
    root.setTop(menuBar);

    // Center: Main split pane with torrent list and details
    createMainContent();
    root.setCenter(mainSplitPane);

    // Bottom: Modern status bar
    statusBar = createStatusBar();
    root.setBottom(statusBar);

    // Start status bar update timer
    startStatusBarUpdates();

    return root;
  }

  /**
   * Create the main content area with download list and detail tabs.
   */
  private void createMainContent() {
    // Download manager panel (contains table + toolbar)
    downloadManagerPanel = new DownloadManagerPanel(this);
    VBox downloadArea = downloadManagerPanel.createPanel();
    VBox.setVgrow(downloadArea, Priority.ALWAYS);

    // Details tab pane (card-style tabs)
    detailsTabPane = createDetailsTabPane();

    // Wrap details in a container with padding
    VBox detailsContainer = new VBox(detailsTabPane);
    detailsContainer.setPadding(new Insets(8, 16, 16, 16));
    VBox.setVgrow(detailsTabPane, Priority.ALWAYS);

    // Create split pane (65/35 split)
    mainSplitPane = new SplitPane(downloadArea, detailsContainer);
    mainSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
    mainSplitPane.setDividerPositions(0.65);
    VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
  }

  /**
   * Create the details tab pane with card-style tabs.
   */
  private TabPane createDetailsTabPane() {
    TabPane tabPane = new TabPane();
    tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabPane.getStyleClass().add("details-tab-pane");

    // Transfer Statistics tab
    Tab statsTab = new Tab("Transfer Statistics");
    statsTab.setClosable(false);
    statisticsPanel = new StatisticsPanel();
    statsTab.setContent(statisticsPanel.createPanel());
    tabPane.getTabs().add(statsTab);

    // Files tab
    Tab filesTab = new Tab("Files");
    filesTab.setClosable(false);
    fileListPanel = new FileListPanel();
    filesTab.setContent(fileListPanel);
    tabPane.getTabs().add(filesTab);

    // Trackers tab
    Tab trackersTab = new Tab("Trackers");
    trackersTab.setClosable(false);
    trackerListPanel = new TrackerListPanel();
    trackersTab.setContent(trackerListPanel);
    tabPane.getTabs().add(trackersTab);

    // Peers tab
    Tab peersTab = new Tab("Peers");
    peersTab.setClosable(false);
    peerListPanel = new PeerListPanel();
    peersTab.setContent(peerListPanel);
    tabPane.getTabs().add(peersTab);

    // Pieces tab
    Tab piecesTab = new Tab("Pieces");
    piecesTab.setClosable(false);
    pieceMapPanel = new PieceMapPanel();
    piecesTab.setContent(pieceMapPanel);
    tabPane.getTabs().add(piecesTab);

    return tabPane;
  }

  /**
   * Create modern status bar with clean layout.
   * Format: "Downloading x | Seeding y | IN: xx KB/s | OUT: xx KB/s | Peers: xx"
   */
  private HBox createStatusBar() {
    HBox bar = new HBox();
    bar.getStyleClass().add("status-bar");
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setSpacing(0);

    // Downloading section
    HBox downloadingSection = createStatusSection("●", "Downloading", "0");
    downloadingCountLabel = (Label) downloadingSection.getChildren().get(2);

    // Seeding section
    HBox seedingSection = createStatusSection("●", "Seeding", "0");
    Label seedingIndicator = (Label) seedingSection.getChildren().get(0);
    seedingIndicator.setStyle("-fx-text-fill: #5865f2; -fx-font-size: 10px;");
    seedingCountLabel = (Label) seedingSection.getChildren().get(2);

    // Separator
    Region sep1 = createStatusSeparator();

    // Download speed section
    HBox downSpeedSection = new HBox(6);
    downSpeedSection.setAlignment(Pos.CENTER_LEFT);
    Label downLabel = new Label("IN:");
    downLabel.getStyleClass().add("status-text");
    downloadSpeedLabel = new Label("0 KB/s");
    downloadSpeedLabel.getStyleClass().addAll("status-value", "speed-down");
    downSpeedSection.getChildren().addAll(downLabel, downloadSpeedLabel);

    // Upload speed section
    HBox upSpeedSection = new HBox(6);
    upSpeedSection.setAlignment(Pos.CENTER_LEFT);
    upSpeedSection.setPadding(new Insets(0, 0, 0, 16));
    Label upLabel = new Label("OUT:");
    upLabel.getStyleClass().add("status-text");
    uploadSpeedLabel = new Label("0 KB/s");
    uploadSpeedLabel.getStyleClass().addAll("status-value", "speed-up");
    upSpeedSection.getChildren().addAll(upLabel, uploadSpeedLabel);

    // Separator
    Region sep2 = createStatusSeparator();

    // Peers section
    HBox peersSection = new HBox(6);
    peersSection.setAlignment(Pos.CENTER_LEFT);
    Label peersLabel = new Label("Peers:");
    peersLabel.getStyleClass().add("status-text");
    peersCountLabel = new Label("0");
    peersCountLabel.getStyleClass().add("status-value");
    peersSection.getChildren().addAll(peersLabel, peersCountLabel);

    // Spacer to push everything left
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    // App version/name on right
    Label appLabel = new Label("JTorrent v1.0");
    appLabel.getStyleClass().add("status-text");
    appLabel.setStyle("-fx-text-fill: #72767d;");

    bar.getChildren().addAll(
        downloadingSection,
        seedingSection,
        sep1,
        downSpeedSection,
        upSpeedSection,
        sep2,
        peersSection,
        spacer,
        appLabel);

    return bar;
  }

  /**
   * Create a status bar section with indicator, label, and value.
   */
  private HBox createStatusSection(String indicator, String labelText, String valueText) {
    HBox section = new HBox(6);
    section.setAlignment(Pos.CENTER_LEFT);
    section.setPadding(new Insets(0, 16, 0, 0));

    Label indicatorLabel = new Label(indicator);
    indicatorLabel.setStyle("-fx-text-fill: #43b581; -fx-font-size: 10px;");

    Label label = new Label(labelText);
    label.getStyleClass().add("status-text");

    Label value = new Label(valueText);
    value.getStyleClass().add("status-value");

    section.getChildren().addAll(indicatorLabel, label, value);
    return section;
  }

  /**
   * Create a status bar separator.
   */
  private Region createStatusSeparator() {
    Region separator = new Region();
    separator.getStyleClass().add("status-separator");
    separator.setMinWidth(1);
    separator.setPrefWidth(1);
    separator.setMaxWidth(1);
    separator.setMinHeight(16);
    HBox.setMargin(separator, new Insets(0, 16, 0, 0));
    return separator;
  }

  /**
   * Start periodic status bar updates.
   */
  private void startStatusBarUpdates() {
    Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
      updateStatusBar();
    }));
    timeline.setCycleCount(Timeline.INDEFINITE);
    timeline.play();
  }

  /**
   * Update status bar with current stats from all downloads.
   */
  private void updateStatusBar() {
    if (downloadManagerPanel == null) {
      return;
    }

    // Reset counters
    downloadingCount = 0;
    seedingCount = 0;
    totalDownloadSpeed = 0;
    totalUploadSpeed = 0;
    totalPeers = 0;

    // Aggregate stats from all downloads
    var downloads = downloadManagerPanel.getDownloads();
    if (downloads != null) {
      for (DownloadItem item : downloads) {
        String status = item.getStatus();
        if (status != null) {
          if (status.contains("Downloading") || status.contains("Connecting")) {
            downloadingCount++;
          } else if (status.contains("Seeding")) {
            seedingCount++;
          }
        }

        totalDownloadSpeed += item.getLastSpeedBytesPerSec();

        var session = item.getSession();
        if (session != null) {
          totalPeers += session.getConnectedPeerCount();
          // Get simulated upload speed from session
          totalUploadSpeed += session.getSimulatedUploadSpeed();
        }
      }
    }

    // Update labels
    downloadingCountLabel.setText(String.valueOf(downloadingCount));
    seedingCountLabel.setText(String.valueOf(seedingCount));
    downloadSpeedLabel.setText(formatSpeed(totalDownloadSpeed));
    uploadSpeedLabel.setText(formatSpeed(totalUploadSpeed));
    peersCountLabel.setText(String.valueOf(totalPeers));
  }

  /**
   * Format bytes per second as human-readable speed.
   */
  private String formatSpeed(long bytesPerSec) {
    if (bytesPerSec <= 0) {
      return "0 KB/s";
    }
    if (bytesPerSec < 1024) {
      return bytesPerSec + " B/s";
    }
    if (bytesPerSec < 1024 * 1024) {
      return String.format("%.1f KB/s", bytesPerSec / 1024.0);
    }
    return String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
  }

  /**
   * Add a torrent file (from drag-and-drop).
   */
  public void addTorrentFile(File file) {
    if (downloadManagerPanel != null) {
      String outputDir = config != null ? config.getDownloadDirectory()
          : System.getProperty("user.home") + "/Downloads";
      downloadManagerPanel.addDownload(file.getAbsolutePath(), outputDir);
      logger.info("Added torrent from drag-and-drop: %s", file.getName());
    }
  }

  /**
   * Add a magnet link (from drag-and-drop or clipboard).
   */
  public void addMagnetLink(String magnetLink) {
    if (downloadManagerPanel != null) {
      String outputDir = config != null ? config.getDownloadDirectory()
          : System.getProperty("user.home") + "/Downloads";
      downloadManagerPanel.addDownload(magnetLink, outputDir);
      logger.info("Added magnet link from drag-and-drop");
    }
  }

  /**
   * Get the application configuration.
   */
  public Config getConfig() {
    return config;
  }

  /**
   * Apply configuration changes to all active components.
   */
  public void applyConfigChanges() {
    logger.info("Applying configuration changes to active sessions");
    if (downloadManagerPanel != null) {
      downloadManagerPanel.applyConfigChanges(config);
    }
    logger.info("Configuration changes applied successfully");
  }

  /**
   * Add a download.
   */
  public void addDownload(String input, String outputDirectory) {
    downloadManagerPanel.addDownload(input, outputDirectory);
  }

  /**
   * Get the download manager panel.
   */
  public DownloadManagerPanel getDownloadManager() {
    return downloadManagerPanel;
  }

  /**
   * Get the statistics panel.
   */
  public StatisticsPanel getStatisticsPanel() {
    return statisticsPanel;
  }

  /**
   * Get the currently selected download item.
   */
  public DownloadItem getSelectedDownloadItem() {
    return selectedDownloadItem;
  }

  /**
   * Shutdown the application and clean up resources.
   */
  public void shutdown() {
    if (downloadManagerPanel != null) {
      downloadManagerPanel.shutdown();
    }
    if (statisticsPanel != null) {
      statisticsPanel.shutdown();
    }
  }

  /**
   * Update the bottom tab panels when a torrent is selected.
   */
  public void updateBottomPanelsForSelection(DownloadItem item) {
    selectedDownloadItem = item;
    if (item == null) {
      // Clear all panels when nothing selected
      if (fileListPanel != null)
        fileListPanel.setSession(null);
      if (trackerListPanel != null)
        trackerListPanel.setSession(null);
      if (peerListPanel != null)
        peerListPanel.setSession(null);
      if (pieceMapPanel != null)
        pieceMapPanel.setSession(null);
      return;
    }

    var session = item.getSession();
    // Only update panels if session exists (may be null initially for magnet links)
    if (session != null) {
      if (fileListPanel != null) {
        fileListPanel.setSession(session);
      }
      if (trackerListPanel != null) {
        trackerListPanel.setSession(session);
      }
      if (peerListPanel != null) {
        peerListPanel.setSession(session);
      }
      if (pieceMapPanel != null) {
        pieceMapPanel.setSession(session);
      }
    }
  }
}
