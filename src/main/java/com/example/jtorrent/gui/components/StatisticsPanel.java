package com.example.jtorrent.gui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Modern statistics panel with card-style layout.
 * Displays transfer speeds, peer counts, and data statistics.
 */
public class StatisticsPanel {

  private Label downloadSpeedLabel;
  private Label uploadSpeedLabel;
  private Label connectedPeersLabel;
  private Label seedersLabel;
  private Label leechersLabel;
  private Label dataDownloadedLabel;
  private Label dataUploadedLabel;
  private Label ratioLabel;

  /**
   * Create the statistics panel with modern card design.
   */
  public VBox createPanel() {
    VBox panel = new VBox(20);
    panel.getStyleClass().add("card-panel");
    panel.setPadding(new Insets(24));

    // Speed section
    HBox speedSection = createSpeedSection();

    // Stats grid
    GridPane statsGrid = createStatsGrid();

    panel.getChildren().addAll(speedSection, statsGrid);
    return panel;
  }

  /**
   * Create the speed display section with large values.
   */
  private HBox createSpeedSection() {
    HBox section = new HBox(48);
    section.setAlignment(Pos.CENTER_LEFT);
    section.setPadding(new Insets(0, 0, 16, 0));
    section.setStyle("-fx-border-color: transparent transparent #40444b transparent; "
        + "-fx-border-width: 0 0 1px 0;");

    // Download speed card
    VBox downloadCard = createSpeedCard("↓ DOWNLOAD", "#43b581");
    downloadSpeedLabel = (Label) downloadCard.getChildren().get(1);

    // Upload speed card
    VBox uploadCard = createSpeedCard("↑ UPLOAD", "#5865f2");
    uploadSpeedLabel = (Label) uploadCard.getChildren().get(1);

    section.getChildren().addAll(downloadCard, uploadCard);
    return section;
  }

  /**
   * Create a speed display card.
   */
  private VBox createSpeedCard(String title, String color) {
    VBox card = new VBox(4);
    card.setAlignment(Pos.CENTER_LEFT);

    Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; -fx-font-weight: bold;");

    Label valueLabel = new Label("0 B/s");
    valueLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 24px; -fx-font-weight: bold;");

    card.getChildren().addAll(titleLabel, valueLabel);
    return card;
  }

  /**
   * Create the statistics grid.
   */
  private GridPane createStatsGrid() {
    GridPane grid = new GridPane();
    grid.getStyleClass().add("stats-grid");
    grid.setHgap(48);
    grid.setVgap(16);
    grid.setPadding(new Insets(16, 0, 0, 0));

    // Row 0: Peers info
    addStatRow(grid, 0, 0, "Connected Peers");
    connectedPeersLabel = addStatValue(grid, 1, 0, "0");

    addStatRow(grid, 2, 0, "Seeders");
    seedersLabel = addStatValue(grid, 3, 0, "0");

    addStatRow(grid, 4, 0, "Leechers");
    leechersLabel = addStatValue(grid, 5, 0, "0");

    // Row 1: Data info
    addStatRow(grid, 0, 1, "Downloaded");
    dataDownloadedLabel = addStatValue(grid, 1, 1, "0 B");

    addStatRow(grid, 2, 1, "Uploaded");
    dataUploadedLabel = addStatValue(grid, 3, 1, "0 B");

    addStatRow(grid, 4, 1, "Ratio");
    ratioLabel = addStatValue(grid, 5, 1, "0.00");

    return grid;
  }

  /**
   * Add a stat label to the grid.
   */
  private void addStatRow(GridPane grid, int col, int row, String text) {
    Label label = new Label(text);
    label.getStyleClass().add("stat-label");
    label.setStyle("-fx-text-fill: #8e9297; -fx-font-size: 12px;");
    grid.add(label, col, row);
  }

  /**
   * Add a stat value to the grid.
   */
  private Label addStatValue(GridPane grid, int col, int row, String value) {
    Label label = new Label(value);
    label.getStyleClass().add("stat-value");
    label.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");
    grid.add(label, col, row);
    return label;
  }

  // Setters for updating values

  public void setDownloadSpeed(String speed) {
    if (downloadSpeedLabel != null) {
      downloadSpeedLabel.setText(speed);
    }
  }

  public void setUploadSpeed(String speed) {
    if (uploadSpeedLabel != null) {
      uploadSpeedLabel.setText(speed);
    }
  }

  public void setConnectedPeers(int count) {
    if (connectedPeersLabel != null) {
      connectedPeersLabel.setText(String.valueOf(count));
    }
  }

  public void setSeeders(int count) {
    if (seedersLabel != null) {
      seedersLabel.setText(String.valueOf(count));
    }
  }

  public void setLeechers(int count) {
    if (leechersLabel != null) {
      leechersLabel.setText(String.valueOf(count));
    }
  }

  public void setDataDownloaded(String data) {
    if (dataDownloadedLabel != null) {
      dataDownloadedLabel.setText(data);
    }
  }

  public void setDataUploaded(String data) {
    if (dataUploadedLabel != null) {
      dataUploadedLabel.setText(data);
    }
  }

  public void setRatio(double ratio) {
    if (ratioLabel != null) {
      ratioLabel.setText(String.format("%.2f", ratio));
    }
  }

  public void shutdown() {
    // Clean up resources if needed
  }
}
