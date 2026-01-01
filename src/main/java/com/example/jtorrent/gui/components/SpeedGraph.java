package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.TransferStats;
import com.example.jtorrent.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Real-time speed graph showing download and upload rates.
 * Features:
 * - Dual line chart (download = green, upload = blue)
 * - Circular buffer for history (configurable time range)
 * - Time range selector: 1min / 5min / 15min / 1hour
 * - Current / Average / Peak speed display
 * - Auto-refresh every second
 */
public class SpeedGraph extends BorderPane {

    private static final Logger logger = Logger.getLogger(SpeedGraph.class);

    private final TransferStats stats;
    private LineChart<Number, Number> chart;
    private XYChart.Series<Number, Number> downloadSeries;
    private XYChart.Series<Number, Number> uploadSeries;

    // Circular buffers for rate history
    private Queue<SpeedDataPoint> downloadHistory;
    private Queue<SpeedDataPoint> uploadHistory;

    private Timeline updateTimeline;
    private int timeCounter = 0;
    private int maxDataPoints = 60; // Default: 1 minute at 1s interval

    // Statistics labels
    private Label currentDownloadLabel;
    private Label currentUploadLabel;
    private Label avgDownloadLabel;
    private Label avgUploadLabel;
    private Label peakDownloadLabel;
    private Label peakUploadLabel;

    // Peak tracking
    private double peakDownloadRate = 0;
    private double peakUploadRate = 0;

    // Previous byte counts for rate calculation
    private long lastDownloadBytes = 0;
    private long lastUploadBytes = 0;

    // Time range options
    private static final int RANGE_1MIN = 60;
    private static final int RANGE_5MIN = 300;
    private static final int RANGE_15MIN = 900;
    private static final int RANGE_1HOUR = 3600;

    /**
     * Constructor for SpeedGraph.
     *
     * @param stats the transfer statistics tracker
     */
    public SpeedGraph(TransferStats stats) {
        this.stats = stats;
        this.downloadHistory = new LinkedList<>();
        this.uploadHistory = new LinkedList<>();
        initializeUI();
        startAutoUpdate();
    }

    /**
     * Constructor without stats (for testing).
     */
    public SpeedGraph() {
        this(null);
    }

    /**
     * Initialize the UI components.
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Top toolbar with controls
        HBox toolbar = createToolbar();
        setTop(toolbar);

        // Chart in center
        chart = createChart();
        setCenter(chart);

        // Statistics panel at bottom
        VBox statsPanel = createStatsPanel();
        setBottom(statsPanel);
    }

    /**
     * Create the toolbar with time range selector.
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label rangeLabel = new Label("Time Range:");
        ComboBox<String> rangeComboBox = new ComboBox<>();
        rangeComboBox.getItems().addAll("1 minute", "5 minutes", "15 minutes", "1 hour");
        rangeComboBox.setValue("1 minute");
        rangeComboBox.setOnAction(e -> {
            String selected = rangeComboBox.getValue();
            switch (selected) {
                case "1 minute":
                    setTimeRange(RANGE_1MIN);
                    break;
                case "5 minutes":
                    setTimeRange(RANGE_5MIN);
                    break;
                case "15 minutes":
                    setTimeRange(RANGE_15MIN);
                    break;
                case "1 hour":
                    setTimeRange(RANGE_1HOUR);
                    break;
            }
        });

        toolbar.getChildren().addAll(rangeLabel, rangeComboBox);
        return toolbar;
    }

    /**
     * Create the line chart.
     */
    private LineChart<Number, Number> createChart() {
        // X axis (time in seconds)
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time (seconds)");
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(maxDataPoints);
        xAxis.setTickUnit(10);

        // Y axis (speed in KB/s)
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Speed (KB/s)");
        yAxis.setAutoRanging(true);

        // Create chart
        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Transfer Speed");
        lineChart.setCreateSymbols(false);
        lineChart.setLegendVisible(true);
        lineChart.setAnimated(false); // Disable animation for smoother updates

        // Download series (green)
        downloadSeries = new XYChart.Series<>();
        downloadSeries.setName("Download");

        // Upload series (blue)
        uploadSeries = new XYChart.Series<>();
        uploadSeries.setName("Upload");

        lineChart.getData().addAll(downloadSeries, uploadSeries);

        // Apply CSS styles
        lineChart.lookup(".chart-series-line.series0").setStyle("-fx-stroke: #4CAF50; -fx-stroke-width: 2px;");
        lineChart.lookup(".chart-series-line.series1").setStyle("-fx-stroke: #2196F3; -fx-stroke-width: 2px;");

        return lineChart;
    }

    /**
     * Create the statistics panel.
     */
    private VBox createStatsPanel() {
        VBox statsPanel = new VBox(10);
        statsPanel.setPadding(new Insets(10));
        statsPanel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1px;");

        // Current speeds
        HBox currentBox = new HBox(20);
        currentBox.setAlignment(Pos.CENTER_LEFT);
        currentDownloadLabel = new Label("↓ Current: 0 KB/s");
        currentDownloadLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        currentUploadLabel = new Label("↑ Current: 0 KB/s");
        currentUploadLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
        currentBox.getChildren().addAll(currentDownloadLabel, currentUploadLabel);

        // Average speeds
        HBox avgBox = new HBox(20);
        avgBox.setAlignment(Pos.CENTER_LEFT);
        avgDownloadLabel = new Label("↓ Average: 0 KB/s");
        avgDownloadLabel.setStyle("-fx-text-fill: #4CAF50;");
        avgUploadLabel = new Label("↑ Average: 0 KB/s");
        avgUploadLabel.setStyle("-fx-text-fill: #2196F3;");
        avgBox.getChildren().addAll(avgDownloadLabel, avgUploadLabel);

        // Peak speeds
        HBox peakBox = new HBox(20);
        peakBox.setAlignment(Pos.CENTER_LEFT);
        peakDownloadLabel = new Label("↓ Peak: 0 KB/s");
        peakDownloadLabel.setStyle("-fx-text-fill: #4CAF50;");
        peakUploadLabel = new Label("↑ Peak: 0 KB/s");
        peakUploadLabel.setStyle("-fx-text-fill: #2196F3;");
        peakBox.getChildren().addAll(peakDownloadLabel, peakUploadLabel);

        statsPanel.getChildren().addAll(currentBox, avgBox, peakBox);
        return statsPanel;
    }

    /**
     * Set the time range for the graph.
     *
     * @param seconds number of seconds to display
     */
    private void setTimeRange(int seconds) {
        maxDataPoints = seconds;

        // Update X axis bounds
        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        xAxis.setUpperBound(seconds);

        // Trim history if needed
        while (downloadHistory.size() > maxDataPoints) {
            downloadHistory.poll();
        }
        while (uploadHistory.size() > maxDataPoints) {
            uploadHistory.poll();
        }

        // Redraw chart
        updateChart();
    }

    /**
     * Start auto-update timer.
     */
    private void startAutoUpdate() {
        updateTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateData()));
        updateTimeline.setCycleCount(Timeline.INDEFINITE);
        updateTimeline.play();
    }

    /**
     * Stop auto-update timer.
     */
    public void stopAutoUpdate() {
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
    }

    /**
     * Update data from stats and refresh chart.
     */
    private void updateData() {
        // Get current byte counts from stats
        long currentDownloadBytes = stats != null ? stats.getDownloadedBytes() : 0;
        long currentUploadBytes = stats != null ? stats.getUploadedBytes() : 0;

        // Calculate rates (bytes per second)
        double downloadRate = (currentDownloadBytes - lastDownloadBytes) / 1024.0; // Convert to KB/s
        double uploadRate = (currentUploadBytes - lastUploadBytes) / 1024.0; // Convert to KB/s

        // Update last byte counts
        lastDownloadBytes = currentDownloadBytes;
        lastUploadBytes = currentUploadBytes;

        // Add to history
        downloadHistory.offer(new SpeedDataPoint(timeCounter, downloadRate));
        uploadHistory.offer(new SpeedDataPoint(timeCounter, uploadRate));

        // Remove old data if exceeds max
        while (downloadHistory.size() > maxDataPoints) {
            downloadHistory.poll();
        }
        while (uploadHistory.size() > maxDataPoints) {
            uploadHistory.poll();
        }

        // Update peak tracking
        if (downloadRate > peakDownloadRate) {
            peakDownloadRate = downloadRate;
        }
        if (uploadRate > peakUploadRate) {
            peakUploadRate = uploadRate;
        }

        // Calculate averages
        double avgDownload = calculateAverage(downloadHistory);
        double avgUpload = calculateAverage(uploadHistory);

        // Update UI on JavaFX thread
        Platform.runLater(() -> {
            // Update chart
            updateChart();

            // Update statistics labels
            currentDownloadLabel.setText(String.format("↓ Current: %.2f KB/s", downloadRate));
            currentUploadLabel.setText(String.format("↑ Current: %.2f KB/s", uploadRate));
            avgDownloadLabel.setText(String.format("↓ Average: %.2f KB/s", avgDownload));
            avgUploadLabel.setText(String.format("↑ Average: %.2f KB/s", avgUpload));
            peakDownloadLabel.setText(String.format("↓ Peak: %.2f KB/s", peakDownloadRate));
            peakUploadLabel.setText(String.format("↑ Peak: %.2f KB/s", peakUploadRate));
        });

        timeCounter++;
    }

    /**
     * Update the chart with current data.
     */
    private void updateChart() {
        downloadSeries.getData().clear();
        uploadSeries.getData().clear();

        // Calculate time offset for X axis
        int offset = Math.max(0, timeCounter - maxDataPoints);

        // Add download data
        for (SpeedDataPoint point : downloadHistory) {
            downloadSeries.getData().add(new XYChart.Data<>(point.time - offset, point.rate));
        }

        // Add upload data
        for (SpeedDataPoint point : uploadHistory) {
            uploadSeries.getData().add(new XYChart.Data<>(point.time - offset, point.rate));
        }
    }

    /**
     * Calculate average rate from history.
     */
    private double calculateAverage(Queue<SpeedDataPoint> history) {
        if (history.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (SpeedDataPoint point : history) {
            sum += point.rate;
        }
        return sum / history.size();
    }

    /**
     * Reset peak statistics.
     */
    public void resetPeaks() {
        peakDownloadRate = 0;
        peakUploadRate = 0;
    }

    /**
     * Clear all data.
     */
    public void clearData() {
        downloadHistory.clear();
        uploadHistory.clear();
        timeCounter = 0;
        resetPeaks();
        Platform.runLater(this::updateChart);
    }

    /**
     * Data point for speed history.
     */
    private static class SpeedDataPoint {
        final int time;
        final double rate;

        SpeedDataPoint(int time, double rate) {
            this.time = time;
            this.rate = rate;
        }
    }
}
