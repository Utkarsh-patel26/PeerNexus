package com.example.jtorrent.tracker;

/**
 * Tracker communication failure.
 */
public class TrackerException extends Exception {

  public TrackerException(String message) {
    super(message);
  }

  public TrackerException(String message, Throwable cause) {
    super(message, cause);
  }
}
