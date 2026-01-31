package com.example.jtorrent.gui.components;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.gui.MainWindowController;
import com.example.jtorrent.logging.Logger;
import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Download manager panel with modern UI design.
 * Contains the main toolbar and torrent table.
 */
public class DownloadManagerPanel {

  private static final Logger logger = Logger.getLogger(DownloadManagerPanel.class);

  private final MainWindowController mainController;
  private TableView<DownloadItem> downloadTable;
  private ObservableList<DownloadItem> downloads;

  // Toolbar buttons (for enabling/disabling based on selection)
  private Button startBtn;
  private Button pauseBtn;
  private Button removeBtn;

  public DownloadManagerPanel(MainWindowController mainController) {
    this.mainController = mainController;
    this.downloads = FXCollections.observableArrayList();
  }

  /**
   * Create the download manager panel.
   */
  public VBox createPanel() {
    VBox panel = new VBox(0);
    panel.getStyleClass().add("download-panel");

    // Modern toolbar with action buttons
    HBox toolbar = createToolbar();
    panel.getChildren().add(toolbar);

    // Torrent table with improved styling
    downloadTable = createDownloadTable();
    VBox.setVgrow(downloadTable, Priority.ALWAYS);
    panel.getChildren().add(downloadTable);

    // Selection listener for button states and bottom panel updates
    downloadTable.getSelectionModel().selectedItemProperty().addListener(
        (obs, oldSelection, newSelection) -> {
          updateButtonStates(newSelection);
          if (mainController != null) {
            mainController.updateBottomPanelsForSelection(newSelection);
          }
        });

    return panel;
  }

  /**
   * Create modern toolbar with styled buttons.
   */
  private HBox createToolbar() {
    HBox toolbar = new HBox(8);
    toolbar.getStyleClass().add("main-toolbar");
    toolbar.setPadding(new Insets(12, 16, 12, 16));
    toolbar.setAlignment(Pos.CENTER_LEFT);

    // Primary action: Add Torrent
    Button addTorrentBtn = new Button("+ Add Torrent");
    addTorrentBtn.getStyleClass().add("btn-primary");
    addTorrentBtn.setTooltip(new Tooltip("Add a .torrent file"));
    addTorrentBtn.setOnAction(e -> showAddTorrentDialog());

    // Secondary action: Add Magnet
    Button addMagnetBtn = new Button("🔗 Magnet");
    addMagnetBtn.getStyleClass().add("btn-secondary");
    addMagnetBtn.setTooltip(new Tooltip("Add a magnet link"));
    addMagnetBtn.setOnAction(e -> showAddMagnetDialog());

    // Separator
    Region sep1 = new Region();
    sep1.setMinWidth(16);

    // Start/Resume button
    startBtn = new Button("▶ Start");
    startBtn.getStyleClass().add("btn-success");
    startBtn.setTooltip(new Tooltip("Start or resume download"));
    startBtn.setDisable(true);
    startBtn.setOnAction(e -> resumeSelected());

    // Pause button
    pauseBtn = new Button("⏸ Pause");
    pauseBtn.getStyleClass().add("btn-warning");
    pauseBtn.setTooltip(new Tooltip("Pause download"));
    pauseBtn.setDisable(true);
    pauseBtn.setOnAction(e -> pauseSelected());

    // Remove button
    removeBtn = new Button("✕ Remove");
    removeBtn.getStyleClass().add("btn-danger");
    removeBtn.setTooltip(new Tooltip("Remove torrent"));
    removeBtn.setDisable(true);
    removeBtn.setOnAction(e -> removeSelected());

    // Spacer
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    toolbar.getChildren().addAll(
        addTorrentBtn,
        addMagnetBtn,
        sep1,
        startBtn,
        pauseBtn,
        removeBtn,
        spacer);

    return toolbar;
  }

  /**
   * Update button enabled/disabled states based on selection.
   */
  private void updateButtonStates(DownloadItem selected) {
    boolean hasSelection = selected != null;
    boolean isRunning = false;
    boolean isPaused = false;

    if (hasSelection && selected.getSession() != null) {
      isRunning = selected.getSession().isRunning();
      isPaused = !isRunning && !selected.getSession().isCompleted();
    }

    startBtn.setDisable(!hasSelection || isRunning);
    pauseBtn.setDisable(!hasSelection || !isRunning);
    removeBtn.setDisable(!hasSelection);
  }

  /**
   * Create the downloads table with modern styling.
   */
  private TableView<DownloadItem> createDownloadTable() {
    TableView<DownloadItem> table = new TableView<>(downloads);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    table.setPlaceholder(createEmptyState());

    // Name column (40% width)
    TableColumn<DownloadItem, String> nameCol = new TableColumn<>("NAME");
    nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
    nameCol.setMinWidth(200);
    nameCol.setPrefWidth(300);
    nameCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String name, boolean empty) {
        super.updateItem(name, empty);
        if (empty || name == null) {
          setText(null);
          setStyle("");
        } else {
          setText(name);
          setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
        }
      }
    });

    // Size column
    TableColumn<DownloadItem, String> sizeCol = new TableColumn<>("SIZE");
    sizeCol.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
    sizeCol.setMinWidth(80);
    sizeCol.setPrefWidth(100);

    // Progress column with percentage
    TableColumn<DownloadItem, Double> progressCol = new TableColumn<>("PROGRESS");
    progressCol.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
    progressCol.setCellFactory(col -> new ProgressBarCell());
    progressCol.setMinWidth(150);
    progressCol.setPrefWidth(180);

    // Speed column
    TableColumn<DownloadItem, String> speedCol = new TableColumn<>("SPEED");
    speedCol.setCellValueFactory(cellData -> cellData.getValue().speedProperty());
    speedCol.setMinWidth(90);
    speedCol.setPrefWidth(110);
    speedCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String speed, boolean empty) {
        super.updateItem(speed, empty);
        if (empty || speed == null) {
          setText(null);
          setStyle("");
        } else {
          setText(speed);
          setStyle("-fx-text-fill: #43b581; -fx-font-weight: bold;");
        }
      }
    });

    // Peers column
    TableColumn<DownloadItem, String> peersCol = new TableColumn<>("PEERS");
    peersCol.setCellValueFactory(cellData -> cellData.getValue().peersProperty());
    peersCol.setMinWidth(70);
    peersCol.setPrefWidth(80);
    peersCol.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String peers, boolean empty) {
        super.updateItem(peers, empty);
        if (empty || peers == null) {
          setText(null);
          setStyle("");
        } else {
          setText(peers);
          setStyle("-fx-text-fill: #5865f2;");
        }
      }
    });

    // Status column
    TableColumn<DownloadItem, String> statusCol = new TableColumn<>("STATUS");
    statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
    statusCol.setMinWidth(100);
    statusCol.setPrefWidth(120);
    statusCol.setCellFactory(col -> new StatusCell());

    table.getColumns().addAll(nameCol, sizeCol, progressCol, speedCol, peersCol, statusCol);
    return table;
  }

  /**
   * Create empty state placeholder.
   */
  private VBox createEmptyState() {
    VBox emptyState = new VBox(8);
    emptyState.getStyleClass().add("empty-state");
    emptyState.setAlignment(Pos.CENTER);

    Label icon = new Label("📂");
    icon.setStyle("-fx-font-size: 48px;");

    Label title = new Label("No Torrents");
    title.getStyleClass().add("empty-state-title");

    Label subtitle = new Label("Click \"Add Torrent\" or drag & drop a .torrent file");
    subtitle.getStyleClass().add("empty-state-subtitle");

    emptyState.getChildren().addAll(icon, title, subtitle);
    return emptyState;
  }

  /**
   * Show add torrent file dialog.
   */
  public void showAddTorrentDialog() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Torrent File");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Torrent Files", "*.torrent"));

    File selectedFile = fileChooser.showOpenDialog(new Stage());
    if (selectedFile != null) {
      String outputDir = getOutputDirectory();
      addDownload(selectedFile.getAbsolutePath(), outputDir);
    }
  }

  /**
   * Show add magnet link dialog.
   */
  public void showAddMagnetDialog() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Add Magnet Link");
    dialog.setHeaderText("Enter Magnet Link");
    dialog.setContentText("Magnet URI:");
    dialog.getDialogPane().setPrefWidth(500);

    dialog.showAndWait().ifPresent(magnet -> {
      if (!magnet.isEmpty() && magnet.startsWith("magnet:")) {
        String outputDir = getOutputDirectory();
        addDownload(magnet, outputDir);
      }
    });
  }

  /**
   * Get output directory from config or default.
   */
  private String getOutputDirectory() {
    if (mainController != null && mainController.getConfig() != null) {
      return mainController.getConfig().getDownloadDirectory();
    }
    return System.getProperty("user.home") + "/Downloads";
  }

  /**
   * Add a download.
   */
  public void addDownload(String input, String outputDirectory) {
    DownloadItem item = new DownloadItem(input, outputDirectory);
    downloads.add(item);
    downloadTable.getSelectionModel().select(item);

    // Start download in background
    Thread downloadThread = new Thread(() -> {
      try {
        URI inputUri;
        if (input.startsWith("magnet:")) {
          inputUri = new URI(input);
        } else {
          inputUri = new File(input).toURI();
        }

        Config config = new Config();
        Logger log = Logger.getLogger(DownloadManagerPanel.class);

        TorrentSession session = new TorrentSession(
            inputUri,
            Paths.get(outputDirectory),
            config,
            log);
        item.setSession(session);

        Platform.runLater(() -> {
          item.setStatus("Initializing...");
          item.updateFromSession();
          downloadTable.refresh();

          // CRITICAL: Re-notify bottom panels now that session is available
          // The initial selection event fired before session was created
          if (mainController != null && downloadTable.getSelectionModel().getSelectedItem() == item) {
            mainController.updateBottomPanelsForSelection(item);
          }
        });

        // Start session in separate thread
        Thread sessionThread = new Thread(() -> {
          try {
            session.start();
          } catch (Exception e) {
            logger.error("Download failed: %s", e.getMessage());
            Platform.runLater(() -> item.setStatus("Error: " + e.getMessage()));
          }
        });
        sessionThread.setDaemon(true);
        sessionThread.start();

        // Wait for initialization
        long startTime = System.currentTimeMillis();
        while (sessionThread.isAlive() && System.currentTimeMillis() - startTime < 60000) {
          Thread.sleep(500);
        }

        if (session.getDownloadState() != null) {
          Platform.runLater(() -> item.updateFromSession());
        }

      } catch (Exception e) {
        Platform.runLater(() -> item.setStatus("Error: " + e.getMessage()));
      }
    });

    // Progress update thread
    Thread progressThread = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          TorrentSession sess = item.getSession();
          if (sess == null) {
            Thread.sleep(200);
            continue;
          }

          Platform.runLater(() -> {
            item.updateFromSession();
            downloadTable.refresh();
            updateButtonStates(downloadTable.getSelectionModel().getSelectedItem());
          });

          if (mainController != null && mainController.getStatisticsPanel() != null) {
            updateStatisticsPanel();
          }

          if (sess.isCompleted()) {
            break;
          }

          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });

    downloadThread.setDaemon(true);
    progressThread.setDaemon(true);
    downloadThread.start();
    progressThread.start();
  }

  private void pauseSelected() {
    DownloadItem selected = downloadTable.getSelectionModel().getSelectedItem();
    if (selected != null && selected.getSession() != null) {
      try {
        TorrentSession session = selected.getSession();
        if (session.isRunning()) {
          session.stop();
          selected.setStatus("Paused");
          updateButtonStates(selected);
        }
      } catch (Exception e) {
        logger.error("Failed to pause: %s", e.getMessage());
      }
    }
  }

  private void resumeSelected() {
    DownloadItem selected = downloadTable.getSelectionModel().getSelectedItem();
    if (selected != null && selected.getSession() != null) {
      try {
        TorrentSession session = selected.getSession();
        if (!session.isRunning() && !session.isCompleted()) {
          Thread resumeThread = new Thread(() -> {
            try {
              session.start();
            } catch (Exception e) {
              Platform.runLater(() -> selected.setStatus("Error: " + e.getMessage()));
            }
          });
          resumeThread.setDaemon(true);
          resumeThread.start();
          selected.setStatus("Resuming...");
          updateButtonStates(selected);
        }
      } catch (Exception e) {
        logger.error("Failed to resume: %s", e.getMessage());
      }
    }
  }

  private void removeSelected() {
    DownloadItem selected = downloadTable.getSelectionModel().getSelectedItem();
    if (selected != null) {
      try {
        TorrentSession session = selected.getSession();
        if (session != null && session.isRunning()) {
          session.stop();
        }
      } catch (Exception e) {
        logger.warn("Error stopping session: %s", e.getMessage());
      }
      downloads.remove(selected);
    }
  }

  /**
   * Update statistics panel with aggregated data.
   */
  private void updateStatisticsPanel() {
    if (mainController == null || mainController.getStatisticsPanel() == null) {
      return;
    }

    Platform.runLater(() -> {
      try {
        DownloadItem selected = downloadTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
          return;
        }

        TorrentSession sess = selected.getSession();
        long downloadedBytes = 0;
        long uploadedBytes = 0;
        long uploadSpeed = 0;
        if (sess != null && sess.getDownloadState() != null) {
          downloadedBytes = sess.getDownloadState().getDownloadedBytes();
          uploadedBytes = sess.getTotalSimulatedUpload();
          uploadSpeed = sess.getSimulatedUploadSpeed();
        }

        mainController.getStatisticsPanel().setDownloadSpeed(
            formatBytes(selected.getLastSpeedBytesPerSec()) + "/s");
        mainController.getStatisticsPanel().setUploadSpeed(formatBytes(uploadSpeed) + "/s");
        mainController.getStatisticsPanel().setDataDownloaded(formatBytes(downloadedBytes));
        mainController.getStatisticsPanel().setDataUploaded(formatBytes(uploadedBytes));

        int connectedPeers = sess != null ? sess.getConnectedPeerCount() : 0;
        int trackerSeeders = sess != null ? sess.getTrackerSeeders() : 0;
        int trackerLeechers = sess != null ? sess.getTrackerLeechers() : 0;

        mainController.getStatisticsPanel().setConnectedPeers(Math.max(0, connectedPeers));
        mainController.getStatisticsPanel().setSeeders(Math.max(0, trackerSeeders));
        mainController.getStatisticsPanel().setLeechers(Math.max(0, trackerLeechers));
      } catch (Exception ignored) {
      }
    });
  }

  private static String formatBytes(long bytes) {
    if (bytes <= 0) {
      return "0 B";
    }
    final String[] units = { "B", "KB", "MB", "GB" };
    int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
    digitGroups = Math.min(digitGroups, units.length - 1);
    return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
  }

  // Public methods for external access
  public ObservableList<DownloadItem> getDownloads() {
    return downloads;
  }

  public void startSelectedDownload() {
    resumeSelected();
  }

  public void stopSelectedDownload() {
    pauseSelected();
  }

  public void removeSelectedDownload() {
    removeSelected();
  }

  public void showPropertiesForSelected() {
    // TODO: Implement properties dialog
  }

  public void recheckSelected() {
    DownloadItem selected = downloadTable.getSelectionModel().getSelectedItem();
    if (selected != null && selected.getSession() != null) {
      try {
        selected.getSession().forceRecheck();
      } catch (Exception e) {
        logger.error("Recheck failed: %s", e.getMessage());
      }
    }
  }

  public void forceReannounceSelected() {
    DownloadItem selected = downloadTable.getSelectionModel().getSelectedItem();
    if (selected != null && selected.getSession() != null) {
      try {
        selected.getSession().forceReannounce();
      } catch (Exception e) {
        logger.error("Reannounce failed: %s", e.getMessage());
      }
    }
  }

  public void refresh() {
    downloadTable.refresh();
  }

  public void shutdown() {
    for (DownloadItem item : downloads) {
      if (item.getSession() != null && item.getSession().isRunning()) {
        try {
          item.getSession().stop();
        } catch (Exception ignored) {
        }
      }
    }
  }

  public void applyConfigChanges(Config config) {
    logger.info("Applied configuration changes");
  }

  /**
   * Modern progress bar cell with percentage text overlay.
   */
  private static class ProgressBarCell extends TableCell<DownloadItem, Double> {
    private final StackPane container;
    private final ProgressBar progressBar;
    private final Label percentLabel;

    public ProgressBarCell() {
      progressBar = new ProgressBar(0);
      progressBar.setMaxWidth(Double.MAX_VALUE);
      progressBar.setPrefHeight(22);
      progressBar.setStyle("-fx-accent: linear-gradient(to right, #5865f2, #7289da);");

      percentLabel = new Label("0%");
      percentLabel.setStyle(
          "-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; "
              + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 2, 0, 0, 1);");

      container = new StackPane(progressBar, percentLabel);
      container.setAlignment(Pos.CENTER);
      container.setPadding(new Insets(4, 8, 4, 8));
    }

    @Override
    protected void updateItem(Double progress, boolean empty) {
      super.updateItem(progress, empty);
      if (empty || progress == null) {
        setGraphic(null);
      } else {
        double clamped = Math.min(1.0, Math.max(0.0, progress));
        progressBar.setProgress(clamped);
        percentLabel.setText(String.format("%.1f%%", clamped * 100));

        // Green when complete
        if (clamped >= 1.0) {
          progressBar.setStyle("-fx-accent: linear-gradient(to right, #43b581, #3ca374);");
        } else {
          progressBar.setStyle("-fx-accent: linear-gradient(to right, #5865f2, #7289da);");
        }

        setGraphic(container);
      }
    }
  }

  /**
   * Status cell with color coding.
   */
  private static class StatusCell extends TableCell<DownloadItem, String> {
    @Override
    protected void updateItem(String status, boolean empty) {
      super.updateItem(status, empty);
      if (empty || status == null) {
        setText(null);
        setStyle("");
      } else {
        setText(status);
        String color = "#dcddde";
        String weight = "normal";

        if (status.contains("Downloading") || status.contains("Connecting")) {
          color = "#43b581";
          weight = "bold";
        } else if (status.contains("Seeding") || status.contains("Complete")) {
          color = "#5865f2";
          weight = "bold";
        } else if (status.contains("Paused")) {
          color = "#faa61a";
        } else if (status.contains("Error")) {
          color = "#f04747";
          weight = "bold";
        }

        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: " + weight + ";");
      }
    }
  }
}
