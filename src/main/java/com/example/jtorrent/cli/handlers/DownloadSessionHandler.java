package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.config.Config;
import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.logging.Logger;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handler for starting a download session.
 */
public final class DownloadSessionHandler implements CommandHandler {

  private static final String VERSION = "1.0.0";
  private static final int KB_TO_BYTES = 1024;

  @Override
  public void execute(CliArgs args) {
    try {
      Config config = loadConfig(args);
      applyCommandLineOverrides(config, args);

      Logger logger = initializeLogger(config);
      logger.info("JTorrent CLI v%s starting...", VERSION);
      logger.info("Input: %s", args.input);
      logger.info("Output: %s", args.output);

      URI inputUri = parseInput(args.input);
      Path outputDir = Path.of(args.output);

      TorrentSession session = new TorrentSession(inputUri, outputDir, config, logger);
      setupShutdownHook(session, logger);

      session.start();

      if (session.isCompleted()) {
        logger.info("Download completed successfully!");
        System.exit(0);
      } else {
        logger.info("Session stopped.");
        System.exit(0);
      }

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static Config loadConfig(CliArgs args) throws Exception {
    if (args.configFile != null) {
      Path configPath = Path.of(args.configFile);
      if (!Files.exists(configPath)) {
        System.err.println("Error: Config file not found: " + args.configFile);
        System.exit(1);
      }
      System.out.println("Loading configuration from: " + args.configFile);
      return Config.fromFile(configPath);
    } else {
      System.out.println("Using default configuration");
      return new Config();
    }
  }

  private static void applyCommandLineOverrides(Config config, CliArgs args) {
    if (args.maxUpload != null) {
      config.setMaxUploadBytesPerSec(args.maxUpload * (long) KB_TO_BYTES);
    }
    if (args.maxDownload != null) {
      config.setMaxDownloadBytesPerSec(args.maxDownload * (long) KB_TO_BYTES);
    }
    if (args.output != null) {
      config.setDownloadDirectory(args.output);
    }
    if (args.verbose) {
      config.setLogLevel("DEBUG");
    }
  }

  private static Logger initializeLogger(Config config) {
    Logger logger = Logger.getLogger(DownloadSessionHandler.class);
    if (config.getLogLevel() != null) {
      Logger.setGlobalLevel(config.getLogLevel());
    }
    if (config.getLogFile() != null) {
      Logger.setLogFile(Path.of(config.getLogFile()));
    }
    return logger;
  }

  private static URI parseInput(String input) {
    if (input.startsWith("magnet:")) {
      return URI.create(input);
    } else {
      File file = new File(input);
      if (!file.exists()) {
        System.err.println("Error: Input file not found: " + input);
        System.exit(1);
      }
      return file.toURI();
    }
  }

  private static void setupShutdownHook(TorrentSession session, Logger logger) {
    Thread shutdownHook = new Thread(() -> {
      System.out.println();
      logger.info("Shutdown signal received, stopping session...");
      session.stop();
    }, "Shutdown-Hook");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }
}
