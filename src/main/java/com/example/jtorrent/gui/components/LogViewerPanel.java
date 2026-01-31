package com.example.jtorrent.gui.components;

import com.example.jtorrent.logging.LogEntry;
import com.example.jtorrent.logging.LogStore;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Advanced log viewer panel with filtering, per-torrent isolation, and live
 * updates.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Per-torrent log viewing
 * <li>Level-based filtering
 * <li>Category filtering
 * <li>Text search
 * <li>Live log streaming
 * <li>Copy and export
 * <li>Color-coded log levels
 * <li>Auto-scroll option
 * </ul>
 */
public class LogViewerPanel extends VBox {

    private final LogStore logStore;

    // UI Components
    private ComboBox<String> torrentSelector;
    private ComboBox<LogEntry.LogLevel> levelFilter;
    private TextField categoryFilter;
    private TextField searchFilter;
    private final TableView<LogEntry> logTable;
    private final ObservableList<LogEntry> logEntries;
    private CheckBox autoScrollCheckbox;
    private CheckBox liveUpdateCheckbox;
    private Label statusLabel;
    private boolean liveUpdatesEnabled = true;
    private Consumer<LogEntry> liveListener;

    /**
     * Create log viewer panel.
     */
    public LogViewerPanel() {
        this(LogStore.getInstance());
    }

    /**
     * Create log viewer panel with custom log store.
     *
     * @param logStore log store to use
     */
    public LogViewerPanel(LogStore logStore) {
        this.logStore = logStore;
        this.logEntries = FXCollections.observableArrayList();

        setSpacing(8);
        setPadding(new Insets(8));
        getStyleClass().add("log-viewer-panel");

        // Create toolbar
        HBox toolbar = createToolbar();

        // Create log table
        logTable = createLogTable();

        // Create status bar
        HBox statusBar = createStatusBar();

        // Layout
        VBox.setVgrow(logTable, Priority.ALWAYS);
        getChildren().addAll(toolbar, logTable, statusBar);

        // Initialize with global logs
        refreshLogs();

        // Setup live updates
        setupLiveUpdates();
    }

    /**
     * Create filter toolbar.
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(4));
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Torrent selector
        Label torrentLabel = new Label("Torrent:");
        torrentSelector = new ComboBox<>();
        torrentSelector.setPrefWidth(150);
        torrentSelector.setPromptText("All (Global)");
        torrentSelector.setOnAction(e -> refreshLogs());
        updateTorrentSelector();

        // Level filter
        Label levelLabel = new Label("Level:");
        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll(LogEntry.LogLevel.values());
        levelFilter.setValue(LogEntry.LogLevel.DEBUG);
        levelFilter.setOnAction(e -> refreshLogs());

        // Category filter
        Label categoryLabel = new Label("Category:");
        categoryFilter = new TextField();
        categoryFilter.setPrefWidth(120);
        categoryFilter.setPromptText("Filter category");
        categoryFilter.textProperty().addListener((obs, old, newVal) -> refreshLogs());

        // Search filter
        Label searchLabel = new Label("Search:");
        searchFilter = new TextField();
        searchFilter.setPrefWidth(150);
        searchFilter.setPromptText("Search messages");
        searchFilter.textProperty().addListener((obs, old, newVal) -> refreshLogs());

        // Auto-scroll checkbox
        autoScrollCheckbox = new CheckBox("Auto-scroll");
        autoScrollCheckbox.setSelected(true);

        // Live update checkbox
        liveUpdateCheckbox = new CheckBox("Live");
        liveUpdateCheckbox.setSelected(true);
        liveUpdateCheckbox.setOnAction(e -> {
            liveUpdatesEnabled = liveUpdateCheckbox.isSelected();
            if (liveUpdatesEnabled) {
                refreshLogs();
            }
        });

        // Refresh button
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> {
            updateTorrentSelector();
            refreshLogs();
        });

        // Clear button
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            String selected = torrentSelector.getValue();
            if (selected == null || selected.isEmpty()) {
                logStore.clearAll();
            } else {
                logStore.clearTorrentLogs(selected);
            }
            refreshLogs();
        });

        // Export button
        Button exportBtn = new Button("Export");
        exportBtn.setOnAction(e -> exportLogs());

        toolbar.getChildren().addAll(
                torrentLabel, torrentSelector,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                levelLabel, levelFilter,
                categoryLabel, categoryFilter,
                searchLabel, searchFilter,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                autoScrollCheckbox, liveUpdateCheckbox,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                refreshBtn, clearBtn, exportBtn);

        return toolbar;
    }

    /**
     * Create log table.
     */
    private TableView<LogEntry> createLogTable() {
        TableView<LogEntry> table = new TableView<>(logEntries);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("log-table");

        // Time column
        TableColumn<LogEntry, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedTime()));
        timeCol.setPrefWidth(90);
        timeCol.setMinWidth(90);

        // Level column with color coding
        TableColumn<LogEntry, String> levelCol = new TableColumn<>("Level");
        levelCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLevel().name()));
        levelCol.setPrefWidth(60);
        levelCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "ERROR" -> setStyle("-fx-text-fill: #ff4444; -fx-font-weight: bold;");
                        case "WARN" -> setStyle("-fx-text-fill: #ffaa00;");
                        case "INFO" -> setStyle("-fx-text-fill: #44ff44;");
                        case "DEBUG" -> setStyle("-fx-text-fill: #888888;");
                        case "TRACE" -> setStyle("-fx-text-fill: #666666;");
                        default -> setStyle("");
                    }
                }
            }
        });

        // Category column
        TableColumn<LogEntry, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getShortCategory()));
        categoryCol.setPrefWidth(120);

        // Thread column
        TableColumn<LogEntry, String> threadCol = new TableColumn<>("Thread");
        threadCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getThreadName()));
        threadCol.setPrefWidth(100);
        threadCol.setVisible(false); // Hidden by default

        // Message column
        TableColumn<LogEntry, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMessage()));

        table.getColumns().addAll(timeCol, levelCol, categoryCol, threadCol, messageCol);

        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> copySelectedToClipboard());
        MenuItem showThreadCol = new MenuItem("Show Thread Column");
        showThreadCol.setOnAction(e -> threadCol.setVisible(!threadCol.isVisible()));
        contextMenu.getItems().addAll(copyItem, showThreadCol);
        table.setContextMenu(contextMenu);

        return table;
    }

    /**
     * Create status bar.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(8);
        statusBar.setPadding(new Insets(4));
        statusBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        statusLabel = new Label("Ready");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label();
        logEntries.addListener((javafx.collections.ListChangeListener<LogEntry>) c -> {
            countLabel.setText(logEntries.size() + " entries");
        });

        statusBar.getChildren().addAll(statusLabel, spacer, countLabel);
        return statusBar;
    }

    /**
     * Update torrent selector with available torrents.
     */
    private void updateTorrentSelector() {
        String currentSelection = torrentSelector.getValue();
        torrentSelector.getItems().clear();
        torrentSelector.getItems().add(""); // Global option

        Set<String> torrents = logStore.getTrackedTorrents();
        torrentSelector.getItems().addAll(torrents);

        if (currentSelection != null && torrents.contains(currentSelection)) {
            torrentSelector.setValue(currentSelection);
        }
    }

    /**
     * Refresh displayed logs based on current filters.
     */
    private void refreshLogs() {
        String torrentId = torrentSelector.getValue();
        if (torrentId != null && torrentId.isEmpty()) {
            torrentId = null;
        }

        LogEntry.LogLevel minLevel = levelFilter.getValue();
        String category = categoryFilter.getText();
        if (category != null && category.isEmpty()) {
            category = null;
        }

        String search = searchFilter.getText();
        if (search != null && search.isEmpty()) {
            search = null;
        }

        List<LogEntry> filtered = logStore.getFilteredLogs(
                torrentId, minLevel, category, search);

        logEntries.setAll(filtered);

        if (autoScrollCheckbox.isSelected() && !logEntries.isEmpty()) {
            logTable.scrollTo(logEntries.size() - 1);
        }

        statusLabel.setText("Showing " + filtered.size() + " entries");
    }

    /**
     * Setup live log updates.
     */
    private void setupLiveUpdates() {
        liveListener = entry -> {
            if (!liveUpdatesEnabled) {
                return;
            }

            Platform.runLater(() -> {
                // Check if entry matches current filters
                String torrentId = torrentSelector.getValue();
                if (torrentId != null && !torrentId.isEmpty()
                        && !torrentId.equals(entry.getTorrentId())) {
                    return;
                }

                LogEntry.LogLevel minLevel = levelFilter.getValue();
                if (!entry.getLevel().isAtLeast(minLevel)) {
                    return;
                }

                String category = categoryFilter.getText();
                if (category != null && !category.isEmpty()
                        && !entry.getCategory().toLowerCase().contains(category.toLowerCase())) {
                    return;
                }

                String search = searchFilter.getText();
                if (search != null && !search.isEmpty()
                        && !entry.getMessage().toLowerCase().contains(search.toLowerCase())) {
                    return;
                }

                // Add entry and auto-scroll
                logEntries.add(entry);

                // Trim if too many entries
                while (logEntries.size() > 5000) {
                    logEntries.remove(0);
                }

                if (autoScrollCheckbox.isSelected()) {
                    logTable.scrollTo(logEntries.size() - 1);
                }
            });
        };

        logStore.addListener(liveListener);
    }

    /**
     * Copy selected entries to clipboard.
     */
    private void copySelectedToClipboard() {
        List<LogEntry> selected = logTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : selected) {
            sb.append(entry.format()).append("\n");
        }

        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(sb.toString());
        clipboard.setContent(content);

        statusLabel.setText("Copied " + selected.size() + " entries to clipboard");
    }

    /**
     * Export logs to file.
     */
    private void exportLogs() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Logs");
        fileChooser.setInitialFileName("peernexus-logs.txt");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"));

        java.io.File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                String torrentId = torrentSelector.getValue();
                if (torrentId != null && torrentId.isEmpty()) {
                    torrentId = null;
                }

                String content = logStore.exportToString(torrentId);
                java.nio.file.Files.writeString(file.toPath(), content);
                statusLabel.setText("Exported to " + file.getName());
            } catch (Exception e) {
                statusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    /**
     * Cleanup resources when panel is closed.
     */
    public void dispose() {
        if (liveListener != null) {
            logStore.removeListener(liveListener);
        }
    }

    /**
     * Set the torrent to view logs for.
     *
     * @param torrentId torrent identifier (null for global)
     */
    public void setSelectedTorrent(String torrentId) {
        if (torrentId == null) {
            torrentSelector.setValue("");
        } else {
            if (!torrentSelector.getItems().contains(torrentId)) {
                torrentSelector.getItems().add(torrentId);
            }
            torrentSelector.setValue(torrentId);
        }
        refreshLogs();
    }
}
