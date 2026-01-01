package com.example.jtorrent.gui.components;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.logging.Logger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Settings dialog with tabs for Network, Bandwidth, DHT, Downloads, and
 * Advanced settings.
 * Allows users to configure all application settings with validation.
 */
public class SettingsDialog extends Stage {

    private static final Logger logger = Logger.getLogger(SettingsDialog.class);

    private final Config config;
    private Config workingConfig; // Working copy for editing

    // Network Tab
    private TextField portField;
    private TextField maxPeersField;
    private TextField connectionTimeoutField;
    private CheckBox enableUpnpCheckBox;

    // Bandwidth Tab
    private TextField maxUploadField;
    private TextField maxDownloadField;
    private TextField uploadSlotsField;

    // DHT Tab
    private CheckBox enableDhtCheckBox;
    private TextField dhtPortField;
    private TextArea bootstrapNodesArea;

    // Downloads Tab
    private TextField downloadDirField;
    private TextField stateDirField;
    private CheckBox autoStartCheckBox;
    private CheckBox seedAfterCompleteCheckBox;

    // Advanced Tab
    private ComboBox<String> logLevelCombo;
    private TextField logFileField;
    private TextField pieceTimeoutField;
    private TextField requestQueueSizeField;

    private boolean configChanged = false;

    public SettingsDialog(Stage owner, Config config) {
        this.config = config;
        this.workingConfig = config.copy(); // Make a working copy

        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("Settings");
        setResizable(true);
        setMinWidth(700);
        setMinHeight(550);

        // Create main layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Create tab pane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Add tabs
        tabPane.getTabs().addAll(
                createNetworkTab(),
                createBandwidthTab(),
                createDhtTab(),
                createDownloadsTab(),
                createAdvancedTab());

        root.setCenter(tabPane);

        // Create button bar
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button applyButton = new Button("Apply");
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");

        applyButton.setOnAction(e -> applySettings());
        okButton.setOnAction(e -> {
            if (applySettings()) {
                close();
            }
        });
        cancelButton.setOnAction(e -> close());

        buttonBar.getChildren().addAll(applyButton, okButton, cancelButton);
        root.setBottom(buttonBar);

        Scene scene = new Scene(root, 700, 550);
        setScene(scene);

        // Load current settings
        loadSettings();
    }

    /**
     * Creates the Network tab.
     */
    private Tab createNetworkTab() {
        Tab tab = new Tab("Network");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;

        // Listen Port
        Label portLabel = new Label("Listen Port:");
        portField = new TextField();
        portField.setPromptText("6881");
        grid.add(portLabel, 0, row);
        grid.add(portField, 1, row);
        grid.add(new Label("Port for incoming connections (1024-65535)"), 2, row);
        row++;

        // Max Peers
        Label maxPeersLabel = new Label("Max Peers:");
        maxPeersField = new TextField();
        maxPeersField.setPromptText("200");
        grid.add(maxPeersLabel, 0, row);
        grid.add(maxPeersField, 1, row);
        grid.add(new Label("Maximum number of connected peers"), 2, row);
        row++;

        // Connection Timeout
        Label timeoutLabel = new Label("Connection Timeout:");
        connectionTimeoutField = new TextField();
        connectionTimeoutField.setPromptText("30");
        grid.add(timeoutLabel, 0, row);
        grid.add(connectionTimeoutField, 1, row);
        grid.add(new Label("Timeout in seconds for peer connections"), 2, row);
        row++;

        // Enable UPnP
        enableUpnpCheckBox = new CheckBox("Enable UPnP/NAT-PMP");
        grid.add(enableUpnpCheckBox, 0, row, 3, 1);
        row++;

        Label upnpNote = new Label("Note: UPnP support coming in Phase 3");
        upnpNote.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
        grid.add(upnpNote, 0, row, 3, 1);

        tab.setContent(grid);
        return tab;
    }

    /**
     * Creates the Bandwidth tab.
     */
    private Tab createBandwidthTab() {
        Tab tab = new Tab("Bandwidth");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;

        // Max Upload Rate
        Label uploadLabel = new Label("Max Upload Rate:");
        maxUploadField = new TextField();
        maxUploadField.setPromptText("0 = unlimited");
        grid.add(uploadLabel, 0, row);
        grid.add(maxUploadField, 1, row);
        grid.add(new Label("KB/s (0 = unlimited)"), 2, row);
        row++;

        // Max Download Rate
        Label downloadLabel = new Label("Max Download Rate:");
        maxDownloadField = new TextField();
        maxDownloadField.setPromptText("0 = unlimited");
        grid.add(downloadLabel, 0, row);
        grid.add(maxDownloadField, 1, row);
        grid.add(new Label("KB/s (0 = unlimited)"), 2, row);
        row++;

        // Upload Slots
        Label slotsLabel = new Label("Upload Slots:");
        uploadSlotsField = new TextField();
        uploadSlotsField.setPromptText("4");
        grid.add(slotsLabel, 0, row);
        grid.add(uploadSlotsField, 1, row);
        grid.add(new Label("Number of concurrent uploads"), 2, row);
        row++;

        // Spacer
        Region spacer = new Region();
        spacer.setPrefHeight(20);
        grid.add(spacer, 0, row, 3, 1);
        row++;

        // Info text
        Label infoLabel = new Label(
                "Bandwidth limiting is active. Changes take effect immediately.");
        infoLabel.setStyle("-fx-font-style: italic;");
        infoLabel.setWrapText(true);
        grid.add(infoLabel, 0, row, 3, 1);

        tab.setContent(grid);
        return tab;
    }

    /**
     * Creates the DHT tab.
     */
    private Tab createDhtTab() {
        Tab tab = new Tab("DHT");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        // Enable DHT
        enableDhtCheckBox = new CheckBox("Enable DHT (Distributed Hash Table)");

        // DHT Port
        HBox portBox = new HBox(10);
        Label portLabel = new Label("DHT Port:");
        dhtPortField = new TextField();
        dhtPortField.setPromptText("6881");
        dhtPortField.setPrefWidth(100);
        Label portNote = new Label("(Usually same as listen port)");
        portNote.setStyle("-fx-text-fill: gray;");
        portBox.getChildren().addAll(portLabel, dhtPortField, portNote);

        // Bootstrap Nodes
        Label bootstrapLabel = new Label("Bootstrap Nodes (one per line):");
        bootstrapNodesArea = new TextArea();
        bootstrapNodesArea.setPrefRowCount(8);
        bootstrapNodesArea.setPromptText("router.bittorrent.com:6881\ndht.transmissionbt.com:6881");

        Label infoLabel = new Label(
                "DHT allows finding peers without trackers. " +
                        "Bootstrap nodes are used to join the DHT network.");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-style: italic;");

        vbox.getChildren().addAll(
                enableDhtCheckBox,
                portBox,
                new Separator(),
                bootstrapLabel,
                bootstrapNodesArea,
                infoLabel);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Creates the Downloads tab.
     */
    private Tab createDownloadsTab() {
        Tab tab = new Tab("Downloads");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        // Download Directory
        HBox downloadDirBox = new HBox(10);
        Label downloadLabel = new Label("Download Directory:");
        downloadLabel.setPrefWidth(150);
        downloadDirField = new TextField();
        downloadDirField.setPrefWidth(300);
        Button browseDlButton = new Button("Browse...");
        browseDlButton.setOnAction(e -> browseDirectory(downloadDirField, "Select Download Directory"));
        downloadDirBox.getChildren().addAll(downloadLabel, downloadDirField, browseDlButton);

        // State Directory
        HBox stateDirBox = new HBox(10);
        Label stateLabel = new Label("State Directory:");
        stateLabel.setPrefWidth(150);
        stateDirField = new TextField();
        stateDirField.setPrefWidth(300);
        Button browseStateButton = new Button("Browse...");
        browseStateButton.setOnAction(e -> browseDirectory(stateDirField, "Select State Directory"));
        stateDirBox.getChildren().addAll(stateLabel, stateDirField, browseStateButton);

        // Options
        autoStartCheckBox = new CheckBox("Auto-start downloads on application launch");
        seedAfterCompleteCheckBox = new CheckBox("Continue seeding after download completes");

        vbox.getChildren().addAll(
                downloadDirBox,
                stateDirBox,
                new Separator(),
                autoStartCheckBox,
                seedAfterCompleteCheckBox);

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Creates the Advanced tab.
     */
    private Tab createAdvancedTab() {
        Tab tab = new Tab("Advanced");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;

        // Log Level
        Label logLevelLabel = new Label("Log Level:");
        logLevelCombo = new ComboBox<>();
        logLevelCombo.getItems().addAll("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
        grid.add(logLevelLabel, 0, row);
        grid.add(logLevelCombo, 1, row);
        row++;

        // Log File
        Label logFileLabel = new Label("Log File:");
        logFileField = new TextField();
        logFileField.setPromptText("logs/jtorrent.log");
        grid.add(logFileLabel, 0, row);
        grid.add(logFileField, 1, row);
        row++;

        // Piece Timeout
        Label pieceTimeoutLabel = new Label("Piece Timeout:");
        pieceTimeoutField = new TextField();
        pieceTimeoutField.setPromptText("30");
        grid.add(pieceTimeoutLabel, 0, row);
        grid.add(pieceTimeoutField, 1, row);
        grid.add(new Label("seconds"), 2, row);
        row++;

        // Request Queue Size
        Label queueSizeLabel = new Label("Request Queue Size:");
        requestQueueSizeField = new TextField();
        requestQueueSizeField.setPromptText("50");
        grid.add(queueSizeLabel, 0, row);
        grid.add(requestQueueSizeField, 1, row);
        grid.add(new Label("max pending requests"), 2, row);
        row++;

        // Warning
        Region spacer = new Region();
        spacer.setPrefHeight(20);
        grid.add(spacer, 0, row, 3, 1);
        row++;

        Label warningLabel = new Label(
                "⚠ Changing these settings may affect performance. " +
                        "Use default values unless you know what you're doing.");
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #d9534f;");
        grid.add(warningLabel, 0, row, 3, 1);

        tab.setContent(grid);
        return tab;
    }

    /**
     * Loads current settings from config into UI fields.
     */
    private void loadSettings() {
        // Network
        portField.setText(String.valueOf(workingConfig.getPort()));
        maxPeersField.setText(String.valueOf(workingConfig.getMaxPeers()));
        connectionTimeoutField.setText("30"); // TODO: Add to Config
        enableUpnpCheckBox.setSelected(false); // TODO: Add to Config

        // Bandwidth
        maxUploadField.setText(String.valueOf(workingConfig.getMaxUploadBytesPerSec() / 1024));
        maxDownloadField.setText(String.valueOf(workingConfig.getMaxDownloadBytesPerSec() / 1024));
        uploadSlotsField.setText(String.valueOf(workingConfig.getUploadSlots()));

        // DHT
        enableDhtCheckBox.setSelected(workingConfig.isDhtEnabled());
        dhtPortField.setText(String.valueOf(workingConfig.getPort()));
        bootstrapNodesArea.setText("router.bittorrent.com:6881\ndht.transmissionbt.com:6881");

        // Downloads
        downloadDirField.setText(workingConfig.getDownloadDirectory());
        stateDirField.setText(workingConfig.getStateDirectory());
        autoStartCheckBox.setSelected(false); // TODO: Add to Config
        seedAfterCompleteCheckBox.setSelected(true); // TODO: Add to Config

        // Advanced
        logLevelCombo.setValue("INFO");
        logFileField.setText("logs/jtorrent.log");
        pieceTimeoutField.setText("30");
        requestQueueSizeField.setText("50");
    }

    /**
     * Applies settings from UI fields to config.
     */
    private boolean applySettings() {
        try {
            // Validate and apply Network settings
            int port = parseInt(portField.getText(), "Port", 1024, 65535);
            int maxPeers = parseInt(maxPeersField.getText(), "Max Peers", 1, 1000);

            // Validate and apply Bandwidth settings
            int maxUpload = parseInt(maxUploadField.getText(), "Max Upload", 0, Integer.MAX_VALUE) * 1024;
            int maxDownload = parseInt(maxDownloadField.getText(), "Max Download", 0, Integer.MAX_VALUE) * 1024;
            int uploadSlots = parseInt(uploadSlotsField.getText(), "Upload Slots", 1, 100);

            // Apply to working config
            workingConfig.setPort(port);
            workingConfig.setMaxPeers(maxPeers);
            workingConfig.setMaxUploadBytesPerSec(maxUpload);
            workingConfig.setMaxDownloadBytesPerSec(maxDownload);
            workingConfig.setUploadSlots(uploadSlots);
            workingConfig.setDhtEnabled(enableDhtCheckBox.isSelected());
            workingConfig.setDownloadDirectory(downloadDirField.getText());
            workingConfig.setStateDirectory(stateDirField.getText());

            // Copy working config back to main config
            config.copyFrom(workingConfig);

            // Save to file
            saveConfigToFile();

            configChanged = true;

            showInfo("Settings Applied", "Settings have been applied successfully.");
            logger.info("Settings applied and saved");

            return true;

        } catch (ValidationException e) {
            showError("Validation Error", e.getMessage());
            return false;
        } catch (Exception e) {
            showError("Error", "Failed to apply settings: " + e.getMessage());
            logger.error("Failed to apply settings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parses an integer from text field with validation.
     */
    private int parseInt(String value, String fieldName, int min, int max) throws ValidationException {
        try {
            int result = Integer.parseInt(value.trim());
            if (result < min || result > max) {
                throw new ValidationException(
                        fieldName + " must be between " + min + " and " + max);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a valid number");
        }
    }

    /**
     * Opens directory chooser dialog.
     */
    private void browseDirectory(TextField textField, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);

        String currentPath = textField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                chooser.setInitialDirectory(currentDir);
            }
        }

        File selectedDir = chooser.showDialog(this);
        if (selectedDir != null) {
            textField.setText(selectedDir.getAbsolutePath());
        }
    }

    /**
     * Saves config to file.
     */
    private void saveConfigToFile() {
        try {
            File configFile = new File("config.json");
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(config.toJson());
            }
            logger.info("Configuration saved to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save configuration: " + e.getMessage());
            throw new RuntimeException("Failed to save configuration", e);
        }
    }

    /**
     * Shows information alert.
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows error alert.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Returns whether config was changed.
     */
    public boolean isConfigChanged() {
        return configChanged;
    }

    /**
     * Custom exception for validation errors.
     */
    private static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
