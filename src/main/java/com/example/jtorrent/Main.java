package com.example.jtorrent;

import com.example.jtorrent.gui.JTorrentApplication;
import javafx.application.Application;

/**
 * Main entry point for JTorrent GUI application.
 * Launches the JavaFX-based graphical user interface.
 */
public final class Main {

  private Main() {
    throw new AssertionError("Main class should not be instantiated");
  }

  /**
   * Main entry point. Launches the GUI application.
   *
   * @param args command-line arguments (passed to JavaFX)
   */
  public static void main(String[] args) {
    Application.launch(JTorrentApplication.class, args);
  }
}
