package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.parser.FileEntry;
import com.example.jtorrent.parser.TorrentFile;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.util.List;

/**
 * Panel displaying files in a torrent with progress indicators.
 */
public class FileListPanel extends BorderPane {

    private TableView<FileInfo> fileTable;
    private ObservableList<FileInfo> files;
    private TorrentSession session;
    private Timeline refreshTimeline;
    private boolean filesLoaded = false;

    public FileListPanel() {
        this.files = FXCollections.observableArrayList();
        initializeUI();
        startAutoRefresh();
    }

    private void initializeUI() {
        fileTable = new TableView<>();
        @SuppressWarnings("deprecation")
        var policy = TableView.CONSTRAINED_RESIZE_POLICY;
        fileTable.setColumnResizePolicy(policy);

        // Name column
        TableColumn<FileInfo, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setPrefWidth(400);

        // Size column
        TableColumn<FileInfo, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());
        sizeCol.setPrefWidth(100);

        // Progress column
        TableColumn<FileInfo, Double> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(data -> data.getValue().progressProperty().asObject());
        progressCol.setPrefWidth(100);
        progressCol.setCellFactory(column -> new TableCell<FileInfo, Double>() {
            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    ProgressBar progressBar = new ProgressBar(progress / 100.0);
                    progressBar.setPrefWidth(80);
                    setGraphic(progressBar);
                    setText(String.format("%.1f%%", progress));
                }
            }
        });

        // Priority column
        TableColumn<FileInfo, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(data -> data.getValue().priorityProperty());
        priorityCol.setPrefWidth(100);

        fileTable.getColumns().addAll(List.of(nameCol, sizeCol, progressCol, priorityCol));
        fileTable.setItems(files);

        VBox.setVgrow(fileTable, Priority.ALWAYS);
        setCenter(fileTable);
        setPadding(new Insets(10));
    }

    public void setSession(TorrentSession session) {
        this.session = session;
        this.filesLoaded = false;
        updateFileList();
    }

    private void updateFileList() {
        Platform.runLater(() -> {
            if (session == null) {
                files.clear();
                filesLoaded = false;
                return;
            }

            TorrentFile torrentFile = session.getTorrentFile();
            if (torrentFile == null) {
                // Metadata not yet loaded (magnet link)
                return;
            }

            // Only reload files if not already loaded
            if (filesLoaded) {
                return;
            }

            files.clear();
            List<FileEntry> fileEntries = torrentFile.files();
            if (fileEntries == null || fileEntries.isEmpty()) {
                // Single file torrent
                files.add(new FileInfo(
                        torrentFile.name(),
                        formatSize(torrentFile.totalLength()),
                        0.0,
                        "Normal"));
            } else {
                // Multi-file torrent
                for (FileEntry entry : fileEntries) {
                    files.add(new FileInfo(
                            String.join("/", entry.path()),
                            formatSize(entry.length()),
                            0.0,
                            "Normal"));
                }
            }
            filesLoaded = true;
        });
    }

    /**
     * Start auto-refresh timer to check for metadata availability.
     */
    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (session != null && !filesLoaded) {
                updateFileList();
            }
        }));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    /**
     * Stop auto-refresh timer.
     */
    public void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static class FileInfo {
        private final SimpleStringProperty name;
        private final SimpleStringProperty size;
        private final SimpleDoubleProperty progress;
        private final SimpleStringProperty priority;

        public FileInfo(String name, String size, double progress, String priority) {
            this.name = new SimpleStringProperty(name);
            this.size = new SimpleStringProperty(size);
            this.progress = new SimpleDoubleProperty(progress);
            this.priority = new SimpleStringProperty(priority);
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public SimpleStringProperty sizeProperty() {
            return size;
        }

        public SimpleDoubleProperty progressProperty() {
            return progress;
        }

        public SimpleStringProperty priorityProperty() {
            return priority;
        }
    }
}
