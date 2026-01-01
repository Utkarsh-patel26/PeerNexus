package com.example.jtorrent.gui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;

/**
 * About dialog displaying application information, version, and supported
 * protocols.
 */
public class AboutDialog extends Stage {

    private static final String APP_NAME = "JTorrent";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "A modern BitTorrent client with JavaFX GUI";
    private static final String GITHUB_URL = "https://github.com/yourusername/jtorrent";
    private static final String LICENSE = "MIT License";

    public AboutDialog(Stage owner) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("About " + APP_NAME);
        setResizable(false);

        // Create main layout
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // App name
        Label nameLabel = new Label(APP_NAME);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 32));

        // Version
        Label versionLabel = new Label("Version " + VERSION);
        versionLabel.setFont(Font.font("System", 16));
        versionLabel.setStyle("-fx-text-fill: #666;");

        // Description
        Label descLabel = new Label(DESCRIPTION);
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(400);
        descLabel.setAlignment(Pos.CENTER);
        descLabel.setStyle("-fx-text-fill: #333;");

        // Separator
        Separator separator1 = new Separator();
        separator1.setPrefWidth(400);

        // Supported BEPs
        Label bepsLabel = new Label("Supported BitTorrent Protocols:");
        bepsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        VBox bepsList = new VBox(5);
        bepsList.setAlignment(Pos.CENTER_LEFT);
        bepsList.setPadding(new Insets(0, 0, 0, 40));
        bepsList.getChildren().addAll(
                new Label("• BEP-3: The BitTorrent Protocol Specification"),
                new Label("• BEP-5: DHT Protocol"),
                new Label("• BEP-9: Extension for Peers to Send Metadata Files"),
                new Label("• BEP-10: Extension Protocol"),
                new Label("• BEP-11: Peer Exchange (PEX)"),
                new Label("• BEP-15: UDP Tracker Protocol"));

        // Separator
        Separator separator2 = new Separator();
        separator2.setPrefWidth(400);

        // System info
        Label javaVersionLabel = new Label("Java: " + System.getProperty("java.version"));
        javaVersionLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        Label javafxVersionLabel = new Label("JavaFX: " + System.getProperty("javafx.version", "21.0.2"));
        javafxVersionLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        // GitHub link
        Hyperlink githubLink = new Hyperlink("GitHub Repository");
        githubLink.setOnAction(e -> openUrl(GITHUB_URL));
        githubLink.setStyle("-fx-font-size: 14px;");

        // License
        Label licenseLabel = new Label(LICENSE);
        licenseLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

        // Close button
        Button closeButton = new Button("Close");
        closeButton.setPrefWidth(100);
        closeButton.setOnAction(e -> close());
        closeButton.setStyle(
                "-fx-background-color: #4CAF50; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 8 20;");

        // Add all components
        root.getChildren().addAll(
                nameLabel,
                versionLabel,
                descLabel,
                separator1,
                bepsLabel,
                bepsList,
                separator2,
                javaVersionLabel,
                javafxVersionLabel,
                githubLink,
                licenseLabel,
                closeButton);

        Scene scene = new Scene(root, 500, 600);
        setScene(scene);
    }

    /**
     * Opens a URL in the default browser.
     */
    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            // Silently fail if browser can't be opened
            System.err.println("Failed to open URL: " + e.getMessage());
        }
    }
}
