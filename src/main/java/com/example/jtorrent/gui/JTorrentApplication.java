package com.example.jtorrent.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Main JavaFX Application for JTorrent GUI.
 * Sets up the primary window and initializes the download manager.
 */
public class JTorrentApplication extends Application {

  private static final String APPLICATION_TITLE = "JTorrent - BitTorrent Client";
  private static final int WINDOW_WIDTH = 1200;
  private static final int WINDOW_HEIGHT = 800;

  private MainWindowController mainController;

  /**
   * Start the application.
   *
   * @param primaryStage the primary window stage
   */
  @Override
  public void start(Stage primaryStage) {
    try {
      // Create main window controller
      mainController = new MainWindowController();
      javafx.scene.layout.BorderPane root = mainController.createMainLayout();

      // Create scene
      Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
      scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());

      // Set up drag and drop support
      setupDragAndDrop(scene);

      // Configure stage
      primaryStage.setTitle(APPLICATION_TITLE);
      primaryStage.setScene(scene);
      primaryStage.setWidth(WINDOW_WIDTH);
      primaryStage.setHeight(WINDOW_HEIGHT);

      // Set window icon if available
      try {
        Image icon = new Image(getClass().getResourceAsStream("/images/icon.png"));
        primaryStage.getIcons().add(icon);
      } catch (Exception e) {
        // Icon not found, continue without it
      }

      // Handle window close
      primaryStage.setOnCloseRequest(event -> {
        mainController.shutdown();
      });

      primaryStage.show();
    } catch (Exception e) {
      e.printStackTrace();
      // Avoid forcing a non-zero process exit for recoverable UI errors.
      // Let JavaFX tear down normally.
      javafx.application.Platform.exit();
    }
  }

  /**
   * Set up drag and drop support for .torrent files and magnet links.
   *
   * @param scene the scene to add drag-and-drop handling to
   */
  private void setupDragAndDrop(Scene scene) {
    // Handle drag over - accept files and strings (for magnet links)
    scene.setOnDragOver(event -> {
      Dragboard db = event.getDragboard();
      if (db.hasFiles() || db.hasString()) {
        // Check if files are .torrent files or string is magnet link
        boolean accept = false;

        if (db.hasFiles()) {
          for (File file : db.getFiles()) {
            if (file.getName().toLowerCase().endsWith(".torrent")) {
              accept = true;
              break;
            }
          }
        }

        if (db.hasString()) {
          String text = db.getString();
          if (text != null && text.toLowerCase().startsWith("magnet:")) {
            accept = true;
          }
        }

        if (accept) {
          event.acceptTransferModes(TransferMode.COPY);
        }
      }
      event.consume();
    });

    // Handle drag entered - visual feedback
    scene.setOnDragEntered(event -> {
      Dragboard db = event.getDragboard();
      if (db.hasFiles() || db.hasString()) {
        scene.getRoot().setStyle("-fx-border-color: #4CAF50; -fx-border-width: 3; -fx-border-style: dashed;");
      }
      event.consume();
    });

    // Handle drag exited - remove visual feedback
    scene.setOnDragExited(event -> {
      scene.getRoot().setStyle("");
      event.consume();
    });

    // Handle drop
    scene.setOnDragDropped(event -> {
      Dragboard db = event.getDragboard();
      boolean success = false;

      if (db.hasFiles()) {
        List<File> files = db.getFiles();
        for (File file : files) {
          if (file.getName().toLowerCase().endsWith(".torrent")) {
            mainController.addTorrentFile(file);
            success = true;
          }
        }
      }

      if (db.hasString()) {
        String text = db.getString();
        if (text != null && text.toLowerCase().startsWith("magnet:")) {
          mainController.addMagnetLink(text);
          success = true;
        }
      }

      // Remove visual feedback
      scene.getRoot().setStyle("");

      event.setDropCompleted(success);
      event.consume();
    });
  }

  /**
   * Stop the application.
   */
  @Override
  public void stop() {
    if (mainController != null) {
      mainController.shutdown();
    }
  }
}
