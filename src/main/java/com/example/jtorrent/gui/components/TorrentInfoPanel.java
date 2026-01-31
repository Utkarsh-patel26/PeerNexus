package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.parser.FileEntry;
import com.example.jtorrent.parser.TorrentFile;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive information panel for a torrent with 5 tabs:
 * 1. General - Basic torrent info
 * 2. Files - File tree with selective download
 * 3. Trackers - Tracker list with status
 * 4. Peers - Peer list panel
 * 5. Piece Map - Visual representation of downloaded pieces
 */
public class TorrentInfoPanel extends TabPane {

    private final TorrentSession session;
    private PeerListPanel peerListPanel;

    /**
     * Constructor for TorrentInfoPanel.
     *
     * @param session the torrent session
     */
    public TorrentInfoPanel(TorrentSession session) {
        this.session = session;
        initializeTabs();
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
    }

    /**
     * Initialize all tabs.
     */
    private void initializeTabs() {
        getTabs().addAll(
                createGeneralTab(),
                createFilesTab(),
                createTrackersTab(),
                createPeersTab(),
                createPieceMapTab());
    }

    /**
     * Tab 1: General Information
     */
    private Tab createGeneralTab() {
        Tab tab = new Tab("General");
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TorrentFile torrentFile = session.getTorrentFile();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        int row = 0;

        // Name
        grid.add(new Label("Name:"), 0, row);
        grid.add(new Label(torrentFile.name()), 1, row++);

        // Info Hash
        grid.add(new Label("Info Hash:"), 0, row);
        grid.add(new Label(torrentFile.infoHashHex()), 1, row++);

        // Total Size
        grid.add(new Label("Total Size:"), 0, row);
        grid.add(new Label(formatSize(torrentFile.totalLength())), 1, row++);

        // Piece Length
        grid.add(new Label("Piece Length:"), 0, row);
        grid.add(new Label(formatSize(torrentFile.pieceLength())), 1, row++);

        // Piece Count
        grid.add(new Label("Piece Count:"), 0, row);
        grid.add(new Label(String.valueOf(torrentFile.pieceCount())), 1, row++);

        // Root dict for optional fields
        Map<String, Object> rootDict = torrentFile.rootDict();

        // Creation Date
        if (rootDict.containsKey("creation date")) {
            grid.add(new Label("Creation Date:"), 0, row);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long creationDate = ((Number) rootDict.get("creation date")).longValue();
            grid.add(new Label(sdf.format(new Date(creationDate * 1000))), 1, row++);
        }

        // Created By
        if (rootDict.containsKey("created by")) {
            grid.add(new Label("Created By:"), 0, row);
            grid.add(new Label((String) rootDict.get("created by")), 1, row++);
        }

        // Comment
        if (rootDict.containsKey("comment")) {
            grid.add(new Label("Comment:"), 0, row);
            TextArea commentArea = new TextArea((String) rootDict.get("comment"));
            commentArea.setWrapText(true);
            commentArea.setPrefRowCount(3);
            commentArea.setEditable(false);
            grid.add(commentArea, 1, row++);
        }

        // Encoding
        if (rootDict.containsKey("encoding")) {
            grid.add(new Label("Encoding:"), 0, row);
            grid.add(new Label((String) rootDict.get("encoding")), 1, row++);
        }

        // Private
        Map<String, Object> info = torrentFile.infoMap();
        boolean isPrivate = info.containsKey("private") && ((Number) info.get("private")).intValue() == 1;
        grid.add(new Label("Private:"), 0, row);
        grid.add(new Label(isPrivate ? "Yes" : "No"), 1, row++);

        content.getChildren().add(grid);
        tab.setContent(content);
        return tab;
    }

    /**
     * Tab 2: Files
     */
    private Tab createFilesTab() {
        Tab tab = new Tab("Files");
        BorderPane content = new BorderPane();
        content.setPadding(new Insets(10));

        // File tree with checkboxes
        TreeView<FileTreeItem> fileTree = new TreeView<>();
        fileTree.setShowRoot(true);

        TorrentFile torrentFile = session.getTorrentFile();
        TreeItem<FileTreeItem> root = new TreeItem<>(
                new FileTreeItem(torrentFile.name(), torrentFile.totalLength(), true));
        root.setExpanded(true);

        // Build file tree
        if (!torrentFile.isSingleFile() && torrentFile.files() != null) {
            // Multi-file mode
            for (FileEntry fileEntry : torrentFile.files()) {
                TreeItem<FileTreeItem> fileItem = new TreeItem<>(
                        new FileTreeItem(
                                fileEntry.path(),
                                fileEntry.length(),
                                false));
                root.getChildren().add(fileItem);
            }
        } else {
            // Single-file mode
            TreeItem<FileTreeItem> fileItem = new TreeItem<>(
                    new FileTreeItem(
                            torrentFile.name(),
                            torrentFile.totalLength(),
                            false));
            root.getChildren().add(fileItem);
        }

        fileTree.setRoot(root);

        // Custom cell factory with checkboxes
        fileTree.setCellFactory(tv -> new FileTreeCell());

        Label infoLabel = new Label(
                "Select files to download. Unselected files will be skipped.");
        infoLabel.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");

        VBox wrapper = new VBox(10, infoLabel, fileTree);
        content.setCenter(wrapper);

        tab.setContent(content);
        return tab;
    }

    /**
     * Tab 3: Trackers
     */
    private Tab createTrackersTab() {
        Tab tab = new Tab("Trackers");
        BorderPane content = new BorderPane();
        content.setPadding(new Insets(10));

        // Tracker table
        TableView<TrackerInfo> trackerTable = new TableView<>();

        TableColumn<TrackerInfo, String> urlCol = new TableColumn<>("URL");
        urlCol.setCellValueFactory(cellData -> cellData.getValue().urlProperty());
        urlCol.setPrefWidth(400);

        TableColumn<TrackerInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(100);

        TableColumn<TrackerInfo, String> seedersCol = new TableColumn<>("Seeders");
        seedersCol.setCellValueFactory(cellData -> cellData.getValue().seedersProperty());
        seedersCol.setPrefWidth(80);

        TableColumn<TrackerInfo, String> leechersCol = new TableColumn<>("Leechers");
        leechersCol.setCellValueFactory(cellData -> cellData.getValue().leechersProperty());
        leechersCol.setPrefWidth(80);

        trackerTable.getColumns().addAll(List.of(urlCol, statusCol, seedersCol, leechersCol));

        // Add trackers from torrent file
        TorrentFile torrentFile = session.getTorrentFile();
        if (torrentFile.primaryAnnounce() != null) {
            trackerTable.getItems().add(
                    new TrackerInfo(torrentFile.primaryAnnounce().toString(), "Unknown", "0", "0"));
        }

        if (torrentFile.announceList() != null) {
            for (URI trackerUri : torrentFile.announceList()) {
                trackerTable.getItems().add(
                        new TrackerInfo(trackerUri.toString(), "Unknown", "0", "0"));
            }
        }

        content.setCenter(trackerTable);
        tab.setContent(content);
        return tab;
    }

    /**
     * Tab 4: Peers
     */
    private Tab createPeersTab() {
        Tab tab = new Tab("Peers");

        // Create peer list panel (reuse component from Phase 2.1.3)
        // Note: You'll need to get the active peers list from session
        peerListPanel = new PeerListPanel();

        tab.setContent(peerListPanel);
        return tab;
    }

    /**
     * Tab 5: Piece Map
     */
    private Tab createPieceMapTab() {
        Tab tab = new Tab("Piece Map");
        BorderPane content = new BorderPane();
        content.setPadding(new Insets(10));

        TorrentFile torrentFile = session.getTorrentFile();
        int numPieces = torrentFile.pieceCount();

        // Create canvas for piece visualization
        Canvas canvas = new Canvas(600, 400);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Calculate grid dimensions
        int cols = (int) Math.ceil(Math.sqrt(numPieces));
        int rows = (int) Math.ceil((double) numPieces / cols);
        double cellWidth = canvas.getWidth() / cols;
        double cellHeight = canvas.getHeight() / rows;

        // Draw piece grid
        for (int i = 0; i < numPieces; i++) {
            int row = i / cols;
            int col = i % cols;
            double x = col * cellWidth;
            double y = row * cellHeight;

            // TODO: Get actual piece status from PieceManager
            // For now, draw empty (white) pieces
            gc.setFill(Color.LIGHTGRAY);
            gc.fillRect(x, y, cellWidth - 1, cellHeight - 1);
        }

        // Legend
        GridPane legend = new GridPane();
        legend.setHgap(10);
        legend.setVgap(5);
        legend.setPadding(new Insets(10));

        Canvas legendDownloaded = new Canvas(20, 20);
        legendDownloaded.getGraphicsContext2D().setFill(Color.GREEN);
        legendDownloaded.getGraphicsContext2D().fillRect(0, 0, 20, 20);

        Canvas legendDownloading = new Canvas(20, 20);
        legendDownloading.getGraphicsContext2D().setFill(Color.YELLOW);
        legendDownloading.getGraphicsContext2D().fillRect(0, 0, 20, 20);

        Canvas legendMissing = new Canvas(20, 20);
        legendMissing.getGraphicsContext2D().setFill(Color.LIGHTGRAY);
        legendMissing.getGraphicsContext2D().fillRect(0, 0, 20, 20);

        legend.add(legendDownloaded, 0, 0);
        legend.add(new Label("Downloaded"), 1, 0);
        legend.add(legendDownloading, 0, 1);
        legend.add(new Label("Downloading"), 1, 1);
        legend.add(legendMissing, 0, 2);
        legend.add(new Label("Missing"), 1, 2);

        VBox wrapper = new VBox(10, canvas, legend);
        content.setCenter(wrapper);

        tab.setContent(content);
        return tab;
    }

    /**
     * Helper: Convert bytes to hex string.
     */
    @SuppressWarnings("unused") // Reserved for future use
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Helper: Format size in human-readable format.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * File tree item for the Files tab.
     */
    private static class FileTreeItem {
        private final String name;
        private final long size;
        private final boolean isDirectory;
        private boolean selected = true;

        public FileTreeItem(String name, long size, boolean isDirectory) {
            this.name = name;
            this.size = size;
            this.isDirectory = isDirectory;
        }

        @Override
        public String toString() {
            if (isDirectory) {
                return name;
            }
            return String.format("%s (%s)", name, formatSize(size));
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

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }

    /**
     * Custom tree cell with checkboxes.
     */
    private static class FileTreeCell extends TreeCell<FileTreeItem> {
        private final CheckBox checkBox;

        public FileTreeCell() {
            this.checkBox = new CheckBox();
            checkBox.setOnAction(e -> {
                FileTreeItem item = getItem();
                if (item != null) {
                    item.setSelected(checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(FileTreeItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                checkBox.setSelected(item.isSelected());
                setText(item.toString());
                setGraphic(checkBox);
            }
        }
    }

    /**
     * Tracker information for the Trackers tab.
     */
    private static class TrackerInfo {
        private final javafx.beans.property.SimpleStringProperty url;
        private final javafx.beans.property.SimpleStringProperty status;
        private final javafx.beans.property.SimpleStringProperty seeders;
        private final javafx.beans.property.SimpleStringProperty leechers;

        public TrackerInfo(String url, String status, String seeders, String leechers) {
            this.url = new javafx.beans.property.SimpleStringProperty(url);
            this.status = new javafx.beans.property.SimpleStringProperty(status);
            this.seeders = new javafx.beans.property.SimpleStringProperty(seeders);
            this.leechers = new javafx.beans.property.SimpleStringProperty(leechers);
        }

        public javafx.beans.property.SimpleStringProperty urlProperty() {
            return url;
        }

        public javafx.beans.property.SimpleStringProperty statusProperty() {
            return status;
        }

        public javafx.beans.property.SimpleStringProperty seedersProperty() {
            return seeders;
        }

        public javafx.beans.property.SimpleStringProperty leechersProperty() {
            return leechers;
        }
    }

    /**
     * Clean up resources.
     */
    public void cleanup() {
        if (peerListPanel != null) {
            peerListPanel.stopAutoRefresh();
        }
    }
}
