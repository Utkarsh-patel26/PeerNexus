package com.example.jtorrent.gui.components;

import com.example.jtorrent.metadata.TorrentCreator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for creating .torrent files from files or directories.
 */
public class TorrentCreatorDialog extends Stage {
    private final TextField inputField;
    private final TextField outputField;
    private final TextArea trackersField;
    private final TextField commentField;
    private final TextField creatorField;
    private final CheckBox privateCheckBox;
    private final ComboBox<String> pieceSizeCombo;
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final Button createButton;

    private File selectedInput;

    public TorrentCreatorDialog() {
        setTitle("Create Torrent");
        initModality(Modality.APPLICATION_MODAL);
        setWidth(700);
        setHeight(600);
        setResizable(false);

        // Main layout
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("Create New Torrent");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Input file/directory section
        GridPane inputPane = new GridPane();
        inputPane.setHgap(10);
        inputPane.setVgap(10);

        Label inputLabel = new Label("Input:");
        inputField = new TextField();
        inputField.setEditable(false);
        inputField.setPromptText("Select file or directory to create torrent from");
        Button browseFileButton = new Button("Browse File");
        Button browseFolderButton = new Button("Browse Folder");

        browseFileButton.setOnAction(e -> selectFile());
        browseFolderButton.setOnAction(e -> selectFolder());

        HBox browseBox = new HBox(10, browseFileButton, browseFolderButton);

        inputPane.add(inputLabel, 0, 0);
        inputPane.add(inputField, 1, 0);
        inputPane.add(browseBox, 2, 0);

        GridPane.setHgrow(inputField, Priority.ALWAYS);

        // Output file section
        Label outputLabel = new Label("Output:");
        outputField = new TextField();
        outputField.setPromptText("Output .torrent file path");
        Button browseOutputButton = new Button("Browse");
        browseOutputButton.setOnAction(e -> selectOutputFile());

        inputPane.add(outputLabel, 0, 1);
        inputPane.add(outputField, 1, 1);
        inputPane.add(browseOutputButton, 2, 1);

        // Trackers section
        Label trackersLabel = new Label("Trackers:");
        trackersField = new TextArea();
        trackersField.setPromptText(
                "Enter tracker URLs (one per line)\nExample:\nudp://tracker.opentrackr.org:1337/announce");
        trackersField.setPrefRowCount(4);

        VBox trackersBox = new VBox(5, trackersLabel, trackersField);

        // Options section
        GridPane optionsPane = new GridPane();
        optionsPane.setHgap(10);
        optionsPane.setVgap(10);

        Label commentLabel = new Label("Comment:");
        commentField = new TextField();
        commentField.setPromptText("Optional comment");

        Label creatorLabel = new Label("Created by:");
        creatorField = new TextField("JTorrent 1.0");

        Label pieceSizeLabel = new Label("Piece size:");
        pieceSizeCombo = new ComboBox<>();
        pieceSizeCombo.getItems().addAll(
                "Auto (recommended)",
                "16 KB",
                "32 KB",
                "64 KB",
                "128 KB",
                "256 KB",
                "512 KB",
                "1 MB",
                "2 MB",
                "4 MB",
                "8 MB",
                "16 MB");
        pieceSizeCombo.setValue("Auto (recommended)");

        privateCheckBox = new CheckBox("Private torrent");
        privateCheckBox.setTooltip(new javafx.scene.control.Tooltip(
                "Enable for private trackers (disables DHT/PEX)"));

        optionsPane.add(commentLabel, 0, 0);
        optionsPane.add(commentField, 1, 0);
        optionsPane.add(creatorLabel, 0, 1);
        optionsPane.add(creatorField, 1, 1);
        optionsPane.add(pieceSizeLabel, 0, 2);
        optionsPane.add(pieceSizeCombo, 1, 2);
        optionsPane.add(privateCheckBox, 0, 3, 2, 1);

        GridPane.setHgrow(commentField, Priority.ALWAYS);
        GridPane.setHgrow(creatorField, Priority.ALWAYS);

        // Progress section
        VBox progressBox = new VBox(5);
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-style: italic;");

        progressBox.getChildren().addAll(progressBar, statusLabel);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        createButton = new Button("Create Torrent");
        createButton.setDefaultButton(true);
        createButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        createButton.setOnAction(e -> createTorrent());

        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        buttonBox.getChildren().addAll(createButton, cancelButton);

        // Add all sections to root
        root.getChildren().addAll(
                titleLabel,
                new Label("Select Input:"),
                inputPane,
                trackersBox,
                new Label("Options:"),
                optionsPane,
                progressBox,
                buttonBox);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File");
        File file = fileChooser.showOpenDialog(this);
        if (file != null) {
            selectedInput = file;
            inputField.setText(file.getAbsolutePath());
            suggestOutputPath();
        }
    }

    private void selectFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Folder");
        File dir = dirChooser.showDialog(this);
        if (dir != null) {
            selectedInput = dir;
            inputField.setText(dir.getAbsolutePath());
            suggestOutputPath();
        }
    }

    private void selectOutputFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Torrent File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Torrent Files", "*.torrent"));

        if (selectedInput != null) {
            fileChooser.setInitialFileName(selectedInput.getName() + ".torrent");
            if (selectedInput.getParentFile() != null) {
                fileChooser.setInitialDirectory(selectedInput.getParentFile());
            }
        }

        File file = fileChooser.showSaveDialog(this);
        if (file != null) {
            String path = file.getAbsolutePath();
            if (!path.endsWith(".torrent")) {
                path += ".torrent";
            }
            outputField.setText(path);
        }
    }

    private void suggestOutputPath() {
        if (selectedInput != null) {
            String outputPath = selectedInput.getAbsolutePath() + ".torrent";
            outputField.setText(outputPath);
        }
    }

    private void createTorrent() {
        // Validate inputs
        if (selectedInput == null || inputField.getText().isEmpty()) {
            showError("Please select an input file or directory");
            return;
        }

        String outputPath = outputField.getText().trim();
        if (outputPath.isEmpty()) {
            showError("Please specify an output file path");
            return;
        }

        // Parse trackers
        List<String> trackers = new ArrayList<>();
        String trackersText = trackersField.getText().trim();
        if (!trackersText.isEmpty()) {
            for (String line : trackersText.split("\n")) {
                String tracker = line.trim();
                if (!tracker.isEmpty()) {
                    trackers.add(tracker);
                }
            }
        }

        // Parse piece size
        int pieceSize = parsePieceSize(pieceSizeCombo.getValue());

        // Build TorrentCreator
        TorrentCreator.Builder builder = new TorrentCreator.Builder();
        builder.addTrackers(trackers);
        builder.setComment(commentField.getText().trim());
        builder.setCreatedBy(creatorField.getText().trim());
        builder.setPrivate(privateCheckBox.isSelected());
        builder.setPieceSize(pieceSize);

        TorrentCreator creator = builder.build();

        // Disable UI during creation
        createButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setText("Starting...");

        // Create torrent in background thread
        new Thread(() -> {
            try {
                Path input = selectedInput.toPath();
                Path output = new File(outputPath).toPath();

                creator.createTorrent(input, output, (current, total, status) -> {
                    Platform.runLater(() -> {
                        double progress = (double) current / total;
                        progressBar.setProgress(progress);
                        statusLabel.setText(status);
                    });
                });

                // Calculate info hash for verification
                byte[] infoHash = TorrentCreator.calculateInfoHash(output);
                String infoHashHex = bytesToHex(infoHash);

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusLabel.setText("Torrent created successfully!");
                    showSuccess("Torrent created successfully!\n\nOutput: " + outputPath
                            + "\n\nInfo Hash: " + infoHashHex);
                    close();
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    createButton.setDisable(false);
                    statusLabel.setText("Failed: " + e.getMessage());
                    showError("Failed to create torrent: " + e.getMessage());
                });
            }
        }, "TorrentCreator").start();
    }

    private int parsePieceSize(String value) {
        if (value.equals("Auto (recommended)")) {
            return 0; // Auto-calculate
        }

        String[] parts = value.split(" ");
        int size = Integer.parseInt(parts[0]);
        String unit = parts[1];

        if (unit.equals("KB")) {
            return size * 1024;
        } else if (unit.equals("MB")) {
            return size * 1024 * 1024;
        }

        return 0;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
