package com.example.jtorrent.automation;

/**
 * Actions that can be executed by automation rules.
 */
public enum RuleAction {
    /**
     * Stop/pause the torrent.
     */
    STOP_TORRENT,

    /**
     * Start/resume the torrent.
     */
    START_TORRENT,

    /**
     * Remove torrent (keeping data).
     */
    REMOVE_TORRENT,

    /**
     * Remove torrent and delete data.
     */
    REMOVE_AND_DELETE,

    /**
     * Set torrent priority to low.
     */
    SET_LOW_PRIORITY,

    /**
     * Set torrent priority to normal.
     */
    SET_NORMAL_PRIORITY,

    /**
     * Set torrent priority to high.
     */
    SET_HIGH_PRIORITY,

    /**
     * Execute external script/command.
     */
    EXECUTE_SCRIPT,

    /**
     * Send notification.
     */
    SEND_NOTIFICATION
}
