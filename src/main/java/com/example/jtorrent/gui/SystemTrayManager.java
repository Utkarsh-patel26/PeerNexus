package com.example.jtorrent.gui;

import com.example.jtorrent.logging.Logger;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * System tray integration for PeerNexus BitTorrent client.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Minimize to tray
 * <li>Tray icon with status indication
 * <li>Right-click context menu
 * <li>Desktop notifications
 * <li>Quick actions from tray
 * </ul>
 */
public class SystemTrayManager {

    private static final Logger logger = Logger.getLogger(SystemTrayManager.class);

    private final Stage mainStage;
    private TrayIcon trayIcon;
    private SystemTray tray;
    private boolean isSupported = false;

    // Status tracking
    @SuppressWarnings("unused") // Reserved for tray tooltip updates
    private int activeDownloads = 0;
    @SuppressWarnings("unused") // Reserved for tray tooltip updates
    private int activePeers = 0;
    @SuppressWarnings("unused") // Reserved for tray tooltip updates
    private long downloadRate = 0;
    @SuppressWarnings("unused") // Reserved for tray tooltip updates
    private long uploadRate = 0;

    /**
     * Create system tray manager.
     *
     * @param mainStage main JavaFX stage
     */
    public SystemTrayManager(Stage mainStage) {
        this.mainStage = mainStage;

        // Check if system tray is supported
        if (!SystemTray.isSupported()) {
            logger.warn("System tray not supported on this platform");
            return;
        }

        this.tray = SystemTray.getSystemTray();
        this.isSupported = true;
    }

    /**
     * Initialize and show tray icon.
     *
     * @return true if successful
     */
    public boolean initialize() {
        if (!isSupported) {
            return false;
        }

        try {
            // Create tray icon
            Image image = createTrayImage(TrayStatus.IDLE);
            trayIcon = new TrayIcon(image, "PeerNexus");
            trayIcon.setImageAutoSize(true);

            // Create popup menu
            PopupMenu popup = createPopupMenu();
            trayIcon.setPopupMenu(popup);

            // Double-click to show window
            trayIcon.addActionListener(e -> Platform.runLater(this::showMainWindow));

            // Add to tray
            tray.add(trayIcon);

            // Configure stage close behavior
            mainStage.setOnCloseRequest(e -> {
                if (isMinimizeToTrayEnabled()) {
                    e.consume();
                    hideToTray();
                }
            });

            logger.info("System tray initialized successfully");
            return true;

        } catch (Exception e) {
            logger.error("Failed to initialize system tray: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Create popup menu for tray icon.
     */
    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();

        // Show/Hide window
        MenuItem showItem = new MenuItem("Show PeerNexus");
        showItem.addActionListener(e -> Platform.runLater(this::showMainWindow));
        popup.add(showItem);

        popup.addSeparator();

        // Pause all
        MenuItem pauseAllItem = new MenuItem("Pause All");
        pauseAllItem.addActionListener(e -> onPauseAll());
        popup.add(pauseAllItem);

        // Resume all
        MenuItem resumeAllItem = new MenuItem("Resume All");
        resumeAllItem.addActionListener(e -> onResumeAll());
        popup.add(resumeAllItem);

        popup.addSeparator();

        // Add torrent from URL
        MenuItem addUrlItem = new MenuItem("Add Torrent from URL...");
        addUrlItem.addActionListener(e -> Platform.runLater(this::onAddFromUrl));
        popup.add(addUrlItem);

        // Open downloads folder
        MenuItem openFolderItem = new MenuItem("Open Downloads Folder");
        openFolderItem.addActionListener(e -> onOpenDownloadsFolder());
        popup.add(openFolderItem);

        popup.addSeparator();

        // Status submenu
        Menu statusMenu = new Menu("Status");
        MenuItem statusItem = new MenuItem("Downloads: 0 | Peers: 0");
        statusItem.setEnabled(false);
        statusMenu.add(statusItem);
        popup.add(statusMenu);

        popup.addSeparator();

        // Exit
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> onExit());
        popup.add(exitItem);

        return popup;
    }

    /**
     * Create tray icon image based on status.
     */
    private Image createTrayImage(TrayStatus status) {
        // Try to load from resources
        try {
            String resourcePath = "/icons/tray-" + status.name().toLowerCase() + ".png";
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            logger.debug("Could not load tray icon from resources: %s", e.getMessage());
        }

        // Generate programmatic icon if resource not found
        return generateIcon(status);
    }

    /**
     * Generate a simple tray icon programmatically.
     */
    private Image generateIcon(TrayStatus status) {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        Color bgColor = switch (status) {
            case DOWNLOADING -> new Color(0, 200, 83); // Green
            case SEEDING -> new Color(33, 150, 243); // Blue
            case PAUSED -> new Color(255, 193, 7); // Yellow
            case ERROR -> new Color(244, 67, 54); // Red
            default -> new Color(158, 158, 158); // Gray (IDLE)
        };

        g2d.setColor(bgColor);
        g2d.fillOval(0, 0, size - 1, size - 1);

        // Border
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawOval(0, 0, size - 1, size - 1);

        // Arrow indicator for downloading/seeding
        if (status == TrayStatus.DOWNLOADING) {
            g2d.setColor(Color.WHITE);
            int[] xPoints = { size / 2, size / 4, 3 * size / 4 };
            int[] yPoints = { 3 * size / 4, size / 4, size / 4 };
            g2d.fillPolygon(xPoints, yPoints, 3); // Down arrow
        } else if (status == TrayStatus.SEEDING) {
            g2d.setColor(Color.WHITE);
            int[] xPoints = { size / 2, size / 4, 3 * size / 4 };
            int[] yPoints = { size / 4, 3 * size / 4, 3 * size / 4 };
            g2d.fillPolygon(xPoints, yPoints, 3); // Up arrow
        }

        g2d.dispose();
        return image;
    }

    /**
     * Update tray icon and tooltip with current status.
     *
     * @param downloads active download count
     * @param peers     connected peer count
     * @param downRate  download rate in bytes/sec
     * @param upRate    upload rate in bytes/sec
     */
    public void updateStatus(int downloads, int peers, long downRate, long upRate) {
        if (trayIcon == null) {
            return;
        }

        this.activeDownloads = downloads;
        this.activePeers = peers;
        this.downloadRate = downRate;
        this.uploadRate = upRate;

        // Update tooltip
        String tooltip = String.format("PeerNexus\nDownloads: %d | Peers: %d\n↓ %s/s | ↑ %s/s",
                downloads, peers,
                formatSize(downRate),
                formatSize(upRate));
        trayIcon.setToolTip(tooltip);

        // Update icon based on status
        TrayStatus status;
        if (downloads > 0) {
            status = downRate > 0 ? TrayStatus.DOWNLOADING : TrayStatus.PAUSED;
        } else if (upRate > 0) {
            status = TrayStatus.SEEDING;
        } else {
            status = TrayStatus.IDLE;
        }
        trayIcon.setImage(createTrayImage(status));
    }

    /**
     * Show a notification balloon.
     *
     * @param title   notification title
     * @param message notification message
     * @param type    message type
     */
    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon == null) {
            return;
        }

        trayIcon.displayMessage(title, message, type);
    }

    /**
     * Show notification when download completes.
     *
     * @param torrentName name of completed torrent
     */
    public void notifyDownloadComplete(String torrentName) {
        showNotification("Download Complete",
                torrentName + " has finished downloading.",
                TrayIcon.MessageType.INFO);
    }

    /**
     * Show notification for error.
     *
     * @param message error message
     */
    public void notifyError(String message) {
        showNotification("PeerNexus Error", message, TrayIcon.MessageType.ERROR);
    }

    /**
     * Hide main window to system tray.
     */
    public void hideToTray() {
        Platform.runLater(() -> {
            mainStage.hide();
            showNotification("PeerNexus Minimized",
                    "PeerNexus is running in the background.",
                    TrayIcon.MessageType.INFO);
        });
    }

    /**
     * Show main window from system tray.
     */
    public void showMainWindow() {
        mainStage.show();
        mainStage.toFront();
        mainStage.requestFocus();
    }

    /**
     * Check if minimize to tray is enabled.
     */
    private boolean isMinimizeToTrayEnabled() {
        // Could be loaded from config
        return true;
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
    }

    // Action handlers (to be connected to actual implementation)

    private void onPauseAll() {
        logger.info("Pause all requested from tray");
        // TODO: Connect to TorrentSession.pauseAll()
        showNotification("PeerNexus", "All downloads paused", TrayIcon.MessageType.INFO);
    }

    private void onResumeAll() {
        logger.info("Resume all requested from tray");
        // TODO: Connect to TorrentSession.resumeAll()
        showNotification("PeerNexus", "All downloads resumed", TrayIcon.MessageType.INFO);
    }

    private void onAddFromUrl() {
        logger.info("Add from URL requested from tray");
        showMainWindow();
        // TODO: Open add torrent dialog
    }

    private void onOpenDownloadsFolder() {
        logger.info("Open downloads folder requested from tray");
        try {
            // TODO: Get actual download path from config
            String downloadPath = System.getProperty("user.home") + "/Downloads";
            Desktop.getDesktop().open(new java.io.File(downloadPath));
        } catch (Exception e) {
            logger.error("Failed to open downloads folder: %s", e.getMessage());
        }
    }

    private void onExit() {
        dispose();
        Platform.exit();
        System.exit(0);
    }

    /**
     * Format byte size to human-readable string.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Check if system tray is supported.
     */
    public boolean isSupported() {
        return isSupported;
    }

    /**
     * Tray icon status enum.
     */
    public enum TrayStatus {
        IDLE,
        DOWNLOADING,
        SEEDING,
        PAUSED,
        ERROR
    }
}
