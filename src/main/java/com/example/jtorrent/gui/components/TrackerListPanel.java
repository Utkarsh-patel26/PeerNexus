package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.TorrentSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.net.URI;
import java.util.List;

/**
 * Panel displaying tracker information and status.
 */
public class TrackerListPanel extends BorderPane {

    private TableView<TrackerInfo> trackerTable;
    private ObservableList<TrackerInfo> trackers;
    private TorrentSession session;
    private Timeline refreshTimeline;
    private boolean trackersLoaded = false;

    public TrackerListPanel() {
        this.trackers = FXCollections.observableArrayList();
        initializeUI();
        startAutoRefresh();
    }

    private void initializeUI() {
        trackerTable = new TableView<>();
        @SuppressWarnings("deprecation")
        var policy = TableView.CONSTRAINED_RESIZE_POLICY;
        trackerTable.setColumnResizePolicy(policy);

        // URL column
        TableColumn<TrackerInfo, String> urlCol = new TableColumn<>("Tracker URL");
        urlCol.setCellValueFactory(data -> data.getValue().urlProperty());
        urlCol.setPrefWidth(300);

        // Status column
        TableColumn<TrackerInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setPrefWidth(150);

        // Seeders column
        TableColumn<TrackerInfo, Number> seedersCol = new TableColumn<>("Seeders");
        seedersCol.setCellValueFactory(data -> data.getValue().seedersProperty());
        seedersCol.setPrefWidth(80);

        // Leechers column
        TableColumn<TrackerInfo, Number> leechersCol = new TableColumn<>("Leechers");
        leechersCol.setCellValueFactory(data -> data.getValue().leechersProperty());
        leechersCol.setPrefWidth(80);

        // Downloaded column
        TableColumn<TrackerInfo, Number> downloadedCol = new TableColumn<>("Downloaded");
        downloadedCol.setCellValueFactory(data -> data.getValue().downloadedProperty());
        downloadedCol.setPrefWidth(100);

        // Message column
        TableColumn<TrackerInfo, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(data -> data.getValue().messageProperty());
        messageCol.setPrefWidth(200);

        trackerTable.getColumns()
                .addAll(List.of(urlCol, statusCol, seedersCol, leechersCol, downloadedCol, messageCol));
        trackerTable.setItems(trackers);

        VBox.setVgrow(trackerTable, Priority.ALWAYS);
        setCenter(trackerTable);
        setPadding(new Insets(10));
    }

    public void setSession(TorrentSession session) {
        this.session = session;
        this.trackersLoaded = false;
        updateTrackerList();
    }

    private void updateTrackerList() {
        Platform.runLater(() -> {
            if (session == null) {
                trackers.clear();
                trackersLoaded = false;
                return;
            }

            if (session.getTorrentFile() == null) {
                // Metadata not yet loaded (magnet link)
                return;
            }

            // Only reload trackers if not already loaded
            if (!trackersLoaded) {
                trackers.clear();

                // Get tracker URLs from torrent file
                List<URI> announceList = session.getTorrentFile().announceList();
                if (announceList != null && !announceList.isEmpty()) {
                    for (URI trackerUrl : announceList) {
                        trackers.add(new TrackerInfo(
                                trackerUrl.toString(),
                                "Not contacted",
                                0,
                                0,
                                0,
                                ""));
                    }
                } else {
                    // Single tracker
                    URI primaryAnnounce = session.getTorrentFile().primaryAnnounce();
                    if (primaryAnnounce != null) {
                        trackers.add(new TrackerInfo(
                                primaryAnnounce.toString(),
                                "Not contacted",
                                0,
                                0,
                                0,
                                ""));
                    }
                }
                trackersLoaded = true;
            }

            // Always update tracker stats
            try {
                int seeders = session.getTrackerSeeders();
                int leechers = session.getTrackerLeechers();
                if (seeders > 0 || leechers > 0) {
                    for (TrackerInfo info : trackers) {
                        info.setSeeders(seeders);
                        info.setLeechers(leechers);
                        info.setStatus("OK");
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Start auto-refresh timer to check for metadata availability and update stats.
     */
    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (session != null) {
                updateTrackerList();
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

    public void updateTrackerStats(String url, String status, int seeders, int leechers, int downloaded,
            String message) {
        for (TrackerInfo info : trackers) {
            if (info.getUrl().equals(url)) {
                info.setStatus(status);
                info.setSeeders(seeders);
                info.setLeechers(leechers);
                info.setDownloaded(downloaded);
                info.setMessage(message);
                break;
            }
        }
    }

    public static class TrackerInfo {
        private final SimpleStringProperty url;
        private final SimpleStringProperty status;
        private final SimpleIntegerProperty seeders;
        private final SimpleIntegerProperty leechers;
        private final SimpleIntegerProperty downloaded;
        private final SimpleStringProperty message;

        public TrackerInfo(String url, String status, int seeders, int leechers, int downloaded, String message) {
            this.url = new SimpleStringProperty(url);
            this.status = new SimpleStringProperty(status);
            this.seeders = new SimpleIntegerProperty(seeders);
            this.leechers = new SimpleIntegerProperty(leechers);
            this.downloaded = new SimpleIntegerProperty(downloaded);
            this.message = new SimpleStringProperty(message);
        }

        public String getUrl() {
            return url.get();
        }

        public SimpleStringProperty urlProperty() {
            return url;
        }

        public void setStatus(String value) {
            status.set(value);
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public void setSeeders(int value) {
            seeders.set(value);
        }

        public SimpleIntegerProperty seedersProperty() {
            return seeders;
        }

        public void setLeechers(int value) {
            leechers.set(value);
        }

        public SimpleIntegerProperty leechersProperty() {
            return leechers;
        }

        public void setDownloaded(int value) {
            downloaded.set(value);
        }

        public SimpleIntegerProperty downloadedProperty() {
            return downloaded;
        }

        public void setMessage(String value) {
            message.set(value);
        }

        public SimpleStringProperty messageProperty() {
            return message;
        }
    }
}
