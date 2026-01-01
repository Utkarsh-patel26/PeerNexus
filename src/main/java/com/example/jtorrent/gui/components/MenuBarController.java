package com.example.jtorrent.gui.components;

import com.example.jtorrent.gui.MainWindowController;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

/**
 * Controller for the application menu bar.
 */
public class MenuBarController {

  private final MainWindowController mainController;
  private MenuBar menuBar;

  public MenuBarController(MainWindowController mainController) {
    this.mainController = mainController;
  }

  /**
   * Create the menu bar.
   *
   * @return the menu bar
   */
  public MenuBar createMenuBar() {
    menuBar = new MenuBar();

    // File menu
    Menu fileMenu = new Menu("File");
    MenuItem addTorrent = new MenuItem("Add Torrent...");
    addTorrent.setOnAction(e -> mainController.getDownloadManager().showAddTorrentDialog());
    MenuItem addMagnet = new MenuItem("Add Magnet Link...");
    addMagnet.setOnAction(e -> mainController.getDownloadManager().showAddMagnetDialog());
    MenuItem createTorrent = new MenuItem("Create Torrent...");
    createTorrent.setOnAction(e -> showCreateTorrent());
    MenuItem exit = new MenuItem("Exit");
    exit.setOnAction(e -> System.exit(0));
    fileMenu.getItems().addAll(addTorrent, addMagnet, createTorrent, new SeparatorMenuItem(), exit);

    // View menu
    Menu viewMenu = new Menu("View");
    MenuItem refresh = new MenuItem("Refresh");
    refresh.setOnAction(e -> mainController.getDownloadManager().refresh());
    viewMenu.getItems().add(refresh);

    // Tools menu
    Menu toolsMenu = new Menu("Tools");
    MenuItem settings = new MenuItem("Settings");
    settings.setOnAction(e -> showSettings());
    toolsMenu.getItems().add(settings);

    // Help menu
    Menu helpMenu = new Menu("Help");
    MenuItem about = new MenuItem("About");
    about.setOnAction(e -> showAbout());
    helpMenu.getItems().add(about);

    menuBar.getMenus().addAll(fileMenu, viewMenu, toolsMenu, helpMenu);
    return menuBar;
  }

  private void showSettings() {
    SettingsDialog dialog = new SettingsDialog(
        (Stage) menuBar.getScene().getWindow(),
        mainController.getConfig());
    dialog.showAndWait();

    // If config changed, apply to running sessions
    if (dialog.isConfigChanged()) {
      mainController.applyConfigChanges();
    }
  }

  private void showAbout() {
    AboutDialog dialog = new AboutDialog((Stage) menuBar.getScene().getWindow());
    dialog.showAndWait();
  }

  private void showCreateTorrent() {
    TorrentCreatorDialog dialog = new TorrentCreatorDialog();
    dialog.initOwner((Stage) menuBar.getScene().getWindow());
    dialog.show();
  }
}
