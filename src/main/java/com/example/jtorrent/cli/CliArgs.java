package com.example.jtorrent.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Container class for parsed command-line arguments.
 */
public final class CliArgs {
  public String input;
  public String output;
  public String configFile;
  public Integer maxUpload;
  public Integer maxDownload;
  public String validateTorrent;
  public String announceTest;
  public String tracker;
  public boolean peerTest = false;
  public String storageTest;
  public boolean runSchedulerTest = false;
  public String magnetUri;
  public boolean verbose = false;
  public boolean showHelp = false;
  public boolean showVersion = false;
  public boolean hasError = false;
  public String errorMessage;

  // Torrent creation fields
  public boolean createTorrent = false;
  public List<String> trackers = new ArrayList<>();
  public String comment;
  public boolean privateTorrent = false;
  public int pieceSize = 0; // 0 = auto
  public boolean force = false;

  @Override
  public String toString() {
    return "CliArgs{"
        + "input='" + input + '\''
        + ", output='" + output + '\''
        + ", configFile='" + configFile + '\''
        + ", maxUpload=" + maxUpload
        + ", maxDownload=" + maxDownload
        + ", validateTorrent='" + validateTorrent + '\''
        + ", announceTest='" + announceTest + '\''
        + ", tracker='" + tracker + '\''
        + ", peerTest=" + peerTest
        + ", storageTest='" + storageTest + '\''
        + ", runSchedulerTest=" + runSchedulerTest
        + ", verbose=" + verbose
        + ", showHelp=" + showHelp
        + ", showVersion=" + showVersion
        + ", createTorrent=" + createTorrent
        + ", trackers=" + trackers
        + ", comment='" + comment + '\''
        + ", privateTorrent=" + privateTorrent
        + ", pieceSize=" + pieceSize
        + ", force=" + force
        + ", hasError=" + hasError
        + ", errorMessage='" + errorMessage + '\''
        + '}';
  }
}