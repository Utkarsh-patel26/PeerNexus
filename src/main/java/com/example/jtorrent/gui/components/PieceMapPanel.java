package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.TorrentSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.BitSet;

/**
 * Panel displaying a visual map of piece download progress.
 */
public class PieceMapPanel extends BorderPane {

    private Canvas canvas;
    private TorrentSession session;
    private Label statsLabel;
    private Timeline refreshTimeline;

    private static final int PIECE_SIZE = 10; // Size of each piece square in pixels
    private static final int PIECES_PER_ROW = 50;

    public PieceMapPanel() {
        initializeUI();
        startAutoRefresh();
    }

    private void initializeUI() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Stats label
        statsLabel = new Label("No torrent selected");
        statsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dcddde;");
        content.getChildren().add(statsLabel);

        // Canvas for piece map
        canvas = new Canvas(800, 600);
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #2f3136; -fx-background-color: #2f3136;");
        content.getChildren().add(scrollPane);

        setCenter(content);
    }

    public void setSession(TorrentSession session) {
        this.session = session;
        updatePieceMap();
    }

    public void updatePieceMap() {
        Platform.runLater(() -> {
            if (session == null) {
                clearCanvas();
                statsLabel.setText("No torrent selected");
                return;
            }

            int totalPieces = 0;
            BitSet bitfield = new BitSet();
            try {
                if (session.getDownloadState() != null) {
                    totalPieces = session.getDownloadState().getPieceCount();
                    bitfield = session.getCompletedPiecesSnapshot();
                } else if (session.getTorrentFile() != null) {
                    totalPieces = session.getTorrentFile().pieceCount();
                }
            } catch (Exception ignored) {
            }

            if (totalPieces <= 0) {
                clearCanvas();
                // Show loading message for magnet links
                if (session.getTorrentFile() == null) {
                    statsLabel.setText("Loading metadata...");
                } else {
                    statsLabel.setText("Pieces: 0 / 0");
                }
                return;
            }

            int rows = (int) Math.ceil(totalPieces / (double) PIECES_PER_ROW);
            double desiredWidth = PIECES_PER_ROW * PIECE_SIZE + 20;
            double desiredHeight = rows * PIECE_SIZE + 20;
            if (canvas.getWidth() != desiredWidth) {
                canvas.setWidth(desiredWidth);
            }
            if (canvas.getHeight() != desiredHeight) {
                canvas.setHeight(desiredHeight);
            }

            // Get graphics context from canvas
            GraphicsContext gc = canvas.getGraphicsContext2D();

            // Clear canvas first
            gc.setFill(Color.web("#2f3136"));
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

            // Draw pieces
            int downloaded = 0;
            for (int i = 0; i < totalPieces; i++) {
                int row = i / PIECES_PER_ROW;
                int col = i % PIECES_PER_ROW;
                int x = col * PIECE_SIZE + 10;
                int y = row * PIECE_SIZE + 10;

                if (bitfield.get(i)) {
                    gc.setFill(Color.web("#43b581")); // Green for downloaded (matches theme)
                    downloaded++;
                } else {
                    gc.setFill(Color.web("#40444b")); // Dark gray for not downloaded
                }

                gc.fillRect(x, y, PIECE_SIZE - 1, PIECE_SIZE - 1);
            }

            // Update stats
            double progress = (downloaded * 100.0) / totalPieces;
            statsLabel.setText(String.format("Pieces: %d / %d (%.1f%%)", downloaded, totalPieces, progress));
        });
    }

    /**
     * Start auto-refresh timer to update piece map periodically.
     */
    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (session != null) {
                updatePieceMap();
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

    private void clearCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#2f3136"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }
}
