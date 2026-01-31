package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.ActivePeer;
import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.peer.PeerConnection;
import com.example.jtorrent.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Panel displaying connected peers with statistics and actions.
 * Shows IP:Port, client name, flags, download/upload rates, and progress.
 */
public class PeerListPanel extends BorderPane {

    private static final Logger logger = Logger.getLogger(PeerListPanel.class);

    private final List<ActivePeer> activePeers;
    private volatile TorrentSession session;
    private TableView<PeerInfo> peerTable;
    private ObservableList<PeerInfo> peers;
    private Label statusLabel;
    private ComboBox<String> filterComboBox;
    private Timeline refreshTimeline;

    // Filter options
    private static final String FILTER_ALL = "All Peers";
    private static final String FILTER_CONNECTED = "Connected";
    private static final String FILTER_SEEDERS = "Seeders";
    private static final String FILTER_LEECHERS = "Leechers";
    private static final String FILTER_UNCHOKED = "Unchoked";

    /**
     * Constructor for PeerListPanel with a list of active peers.
     *
     * @param activePeers the list of active peers
     */
    public PeerListPanel(List<ActivePeer> activePeers) {
        this.activePeers = activePeers;
        this.peers = FXCollections.observableArrayList();
        initializeUI();
        startAutoRefresh();
    }

    /**
     * Constructor for PeerListPanel (creates empty list).
     */
    public PeerListPanel() {
        this(new CopyOnWriteArrayList<>());
    }

    public void setSession(TorrentSession session) {
        this.session = session;
        refreshPeerList();
    }

    /**
     * Initialize the UI components.
     */
    private void initializeUI() {
        // Top toolbar with filter
        HBox toolbar = createToolbar();
        setTop(toolbar);

        // Peer table
        peerTable = createPeerTable();
        VBox.setVgrow(peerTable, Priority.ALWAYS);
        setCenter(peerTable);

        // Status bar
        HBox statusBar = createStatusBar();
        setBottom(statusBar);

        // Context menu
        peerTable.setContextMenu(createContextMenu());

        setPadding(new Insets(10));
    }

    /**
     * Create the toolbar with filter options.
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label filterLabel = new Label("Filter:");
        filterComboBox = new ComboBox<>();
        filterComboBox.getItems().addAll(
                FILTER_ALL,
                FILTER_CONNECTED,
                FILTER_SEEDERS,
                FILTER_LEECHERS,
                FILTER_UNCHOKED);
        filterComboBox.setValue(FILTER_ALL);
        filterComboBox.setOnAction(e -> refreshPeerList());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshPeerList());

        toolbar.getChildren().addAll(filterLabel, filterComboBox, refreshButton);
        return toolbar;
    }

    /**
     * Create the peer table with columns.
     */
    private TableView<PeerInfo> createPeerTable() {
        TableView<PeerInfo> table = new TableView<>();
        table.setItems(peers);

        // IP:Port column
        TableColumn<PeerInfo, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setPrefWidth(150);

        // Client column
        TableColumn<PeerInfo, String> clientCol = new TableColumn<>("Client");
        clientCol.setCellValueFactory(new PropertyValueFactory<>("client"));
        clientCol.setPrefWidth(120);

        // Flags column
        TableColumn<PeerInfo, String> flagsCol = new TableColumn<>("Flags");
        flagsCol.setCellValueFactory(new PropertyValueFactory<>("flags"));
        flagsCol.setPrefWidth(80);

        // Download rate column
        TableColumn<PeerInfo, String> downloadCol = new TableColumn<>("↓ Rate");
        downloadCol.setCellValueFactory(new PropertyValueFactory<>("downloadRate"));
        downloadCol.setPrefWidth(100);

        // Upload rate column
        TableColumn<PeerInfo, String> uploadCol = new TableColumn<>("↑ Rate");
        uploadCol.setCellValueFactory(new PropertyValueFactory<>("uploadRate"));
        uploadCol.setPrefWidth(100);

        // Progress column
        TableColumn<PeerInfo, Double> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(new PropertyValueFactory<>("progress"));
        progressCol.setCellFactory(tc -> new ProgressBarTableCell());
        progressCol.setPrefWidth(150);

        table.getColumns().addAll(addressCol, clientCol, flagsCol, downloadCol, uploadCol, progressCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    /**
     * Create the status bar showing peer counts.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Peers: 0");
        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    /**
     * Create context menu for peer actions.
     */
    private ContextMenu createContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem disconnectItem = new MenuItem("Disconnect");
        disconnectItem.setOnAction(e -> {
            PeerInfo peer = peerTable.getSelectionModel().getSelectedItem();
            if (peer != null) {
                disconnectPeer(peer);
            }
        });

        MenuItem banItem = new MenuItem("Ban Peer");
        banItem.setOnAction(e -> {
            PeerInfo peer = peerTable.getSelectionModel().getSelectedItem();
            if (peer != null) {
                banPeer(peer);
            }
        });

        MenuItem copyIpItem = new MenuItem("Copy IP Address");
        copyIpItem.setOnAction(e -> {
            PeerInfo peer = peerTable.getSelectionModel().getSelectedItem();
            if (peer != null) {
                copyIpAddress(peer);
            }
        });

        contextMenu.getItems().addAll(disconnectItem, banItem, new SeparatorMenuItem(), copyIpItem);
        return contextMenu;
    }

    /**
     * Refresh the peer list based on the current filter.
     */
    public void refreshPeerList() {
        // Keep backward-compatible list, but prefer the live session snapshot.
        List<ActivePeer> resolvedPeers = activePeers;
        try {
            if (session != null) {
                resolvedPeers = session.getActivePeersSnapshot();
            }
        } catch (Exception ignored) {
        }

        final List<ActivePeer> sourcePeers = resolvedPeers;

        Platform.runLater(() -> {
            peers.clear();
            String filter = filterComboBox.getValue();

            int totalPeers = 0;
            int seeders = 0;
            int leechers = 0;
            int unchoked = 0;

            for (ActivePeer activePeer : sourcePeers) {
                if (activePeer == null)
                    continue;

                PeerInfo peerInfo = new PeerInfo(activePeer);

                // Apply filter
                boolean includeInList = false;
                switch (filter) {
                    case FILTER_ALL:
                        includeInList = true;
                        break;
                    case FILTER_CONNECTED:
                        includeInList = activePeer.getConnection().isConnected();
                        break;
                    case FILTER_SEEDERS:
                        includeInList = peerInfo.isSeeder();
                        break;
                    case FILTER_LEECHERS:
                        includeInList = !peerInfo.isSeeder();
                        break;
                    case FILTER_UNCHOKED:
                        includeInList = !peerInfo.isChoked();
                        break;
                }

                if (includeInList) {
                    peers.add(peerInfo);
                }

                // Update counts
                totalPeers++;
                if (peerInfo.isSeeder())
                    seeders++;
                else
                    leechers++;
                if (!peerInfo.isChoked())
                    unchoked++;
            }

            // Update status label
            statusLabel.setText(String.format(
                    "Peers: %d (Seeders: %d, Leechers: %d, Unchoked: %d)",
                    totalPeers, seeders, leechers, unchoked));
        });
    }

    /**
     * Start auto-refresh timer (every 1 second).
     */
    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshPeerList()));
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

    /**
     * Disconnect a peer.
     */
    private void disconnectPeer(PeerInfo peer) {
        logger.info("Disconnecting peer: " + peer.getAddress());
        // TODO: Implement disconnect in TorrentSession
        refreshPeerList();
    }

    /**
     * Ban a peer (add to blacklist).
     */
    private void banPeer(PeerInfo peer) {
        logger.info("Banning peer: " + peer.getAddress());
        // TODO: Implement ban in TorrentSession
        refreshPeerList();
    }

    /**
     * Copy peer IP address to clipboard.
     */
    private void copyIpAddress(PeerInfo peer) {
        String address = peer.getAddress();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(address);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        logger.info("Copied IP address: " + address);
    }

    /**
     * PeerInfo class for table display.
     */
    public static class PeerInfo {
        private final SimpleStringProperty address;
        private final SimpleStringProperty client;
        private final SimpleStringProperty flags;
        private final SimpleStringProperty downloadRate;
        private final SimpleStringProperty uploadRate;
        private final SimpleDoubleProperty progress;
        private final boolean isSeeder;
        private final boolean isChoked;

        public PeerInfo(ActivePeer activePeer) {
            PeerConnection connection = activePeer.getConnection();
            InetSocketAddress addr = activePeer.getAddress();

            this.address = new SimpleStringProperty(addr.getAddress().getHostAddress() + ":" + addr.getPort());
            this.client = new SimpleStringProperty(parseClientName(connection.remotePeerId()));

            // Build flags string
            StringBuilder flagsBuilder = new StringBuilder();
            if (connection.peerInterested())
                flagsBuilder.append("I");
            if (connection.amInterested())
                flagsBuilder.append("i");
            if (connection.peerChoking())
                flagsBuilder.append("C");
            if (connection.amChoking())
                flagsBuilder.append("c");
            if (connection.peerSupportsExtensions())
                flagsBuilder.append("E");
            this.flags = new SimpleStringProperty(flagsBuilder.toString());

            // Calculate rates
            double downRate = activePeer.getDownloadRate();
            this.downloadRate = new SimpleStringProperty(formatRate((long) downRate));
            this.uploadRate = new SimpleStringProperty("0 B/s"); // TODO: Add upload rate tracking

            // Calculate progress (based on bitfield and total pieces)
            double prog = calculateProgress(connection.getPeerBitfield(), connection.getTotalPieces());
            this.progress = new SimpleDoubleProperty(prog);

            this.isSeeder = connection.isPeerSeeder();
            this.isChoked = connection.peerChoking();
        }

        private double calculateProgress(BitSet bitfield, int totalPieces) {
            if (bitfield == null || totalPieces <= 0)
                return 0.0;
            int setBits = bitfield.cardinality();
            return (double) setBits / totalPieces;
        }

        private String parseClientName(byte[] peerId) {
            if (peerId == null || peerId.length < 8) {
                return "Unknown";
            }

            // Parse Azureus-style peer ID (-XX1234-)
            if (peerId[0] == '-' && peerId[7] == '-') {
                String clientCode = new String(peerId, 1, 2);
                String version = String.format("%c.%c.%c.%c", peerId[3], peerId[4], peerId[5], peerId[6]);
                return clientCode + " " + version;
            }

            return "Unknown";
        }

        private String formatRate(long bytesPerSec) {
            if (bytesPerSec == 0)
                return "0 B/s";
            if (bytesPerSec < 1024)
                return bytesPerSec + " B/s";
            if (bytesPerSec < 1024 * 1024)
                return String.format("%.1f KB/s", bytesPerSec / 1024.0);
            return String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
        }

        // Getters for JavaFX properties
        public String getAddress() {
            return address.get();
        }

        public String getClient() {
            return client.get();
        }

        public String getFlags() {
            return flags.get();
        }

        public String getDownloadRate() {
            return downloadRate.get();
        }

        public String getUploadRate() {
            return uploadRate.get();
        }

        public Double getProgress() {
            return progress.get();
        }

        public boolean isSeeder() {
            return isSeeder;
        }

        public boolean isChoked() {
            return isChoked;
        }
    }

    /**
     * Progress bar cell for displaying peer progress with percentage.
     */
    private static class ProgressBarTableCell extends TableCell<PeerInfo, Double> {
        private final ProgressBar progressBar;
        private final Label percentLabel;
        private final HBox container;

        public ProgressBarTableCell() {
            this.progressBar = new ProgressBar();
            this.progressBar.setPrefWidth(80);
            this.percentLabel = new Label();
            this.percentLabel.setMinWidth(50);
            this.percentLabel.setStyle("-fx-font-size: 11px;");
            this.container = new HBox(5);
            this.container.setAlignment(Pos.CENTER_LEFT);
            this.container.getChildren().addAll(progressBar, percentLabel);
        }

        @Override
        protected void updateItem(Double progress, boolean empty) {
            super.updateItem(progress, empty);
            if (empty || progress == null) {
                setGraphic(null);
                setText(null);
            } else {
                progressBar.setProgress(progress);
                percentLabel.setText(String.format("%.1f%%", progress * 100.0));
                setGraphic(container);
            }
        }
    }
}
