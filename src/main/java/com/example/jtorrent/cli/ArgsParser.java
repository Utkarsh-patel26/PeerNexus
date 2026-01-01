package com.example.jtorrent.cli;

/**
 * Parser for command-line arguments.
 */
public final class ArgsParser {

  private ArgsParser() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Parse command-line arguments into a CliArgs object.
   *
   * @param args the command-line arguments
   * @return parsed CliArgs object
   */
  public static CliArgs parse(String[] args) {
    CliArgs result = new CliArgs();

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      switch (arg) {
        case "--help", "-h" -> result.showHelp = true;
        case "--version" -> result.showVersion = true;
        case "--verbose", "-v" -> result.verbose = true;
        case "--peer-test" -> result.peerTest = true;
        case "--run-scheduler-test" -> result.runSchedulerTest = true;
        case "--create" -> result.createTorrent = true;
        case "--private" -> result.privateTorrent = true;
        case "--force", "-f" -> result.force = true;

        case "--validate-torrent" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--validate-torrent requires a file path");
          }
          result.validateTorrent = args[++i];
        }

        case "--announce-test" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--announce-test requires a torrent file path");
          }
          result.announceTest = args[++i];
        }

        case "--tracker" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--tracker requires a URI");
          }
          result.tracker = args[++i];
        }

        case "--storage-test" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--storage-test requires a torrent file path");
          }
          result.storageTest = args[++i];
        }

        case "--magnet" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--magnet requires a magnet URI");
          }
          result.magnetUri = args[++i];
        }

        case "--input", "-i" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--input requires a value");
          }
          result.input = args[++i];
        }

        case "--output", "-o" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--output requires a value");
          }
          result.output = args[++i];
        }

        case "--config", "-c" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--config requires a value");
          }
          result.configFile = args[++i];
        }

        case "--max-upload" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--max-upload requires a value");
          }
          Integer value = parsePositiveInt(args[++i], "--max-upload");
          if (value == null) {
            return setError(result, "--max-upload must be a non-negative integer");
          }
          result.maxUpload = value;
        }

        case "--max-download" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--max-download requires a value");
          }
          Integer value = parsePositiveInt(args[++i], "--max-download");
          if (value == null) {
            return setError(result, "--max-download must be a non-negative integer");
          }
          result.maxDownload = value;
        }

        case "--add-tracker" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--add-tracker requires a tracker URL");
          }
          result.trackers.add(args[++i]);
        }

        case "--comment" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--comment requires a value");
          }
          result.comment = args[++i];
        }

        case "--piece-size" -> {
          if (!hasNextArg(args, i)) {
            return setError(result, "--piece-size requires a value");
          }
          Integer value = parsePositiveInt(args[++i], "--piece-size");
          if (value == null || value < 16384 || value > 16777216) {
            return setError(result,
                "--piece-size must be between 16384 (16KB) and 16777216 (16MB)");
          }
          result.pieceSize = value;
        }

        default -> {
          if (arg.startsWith("-")) {
            return setError(result, "Unknown option: " + arg);
          }
          // Treat as positional argument (input if not set)
          if (result.input == null) {
            result.input = arg;
          }
        }
      }
    }

    return result;
  }

  private static boolean hasNextArg(String[] args, int currentIndex) {
    return currentIndex + 1 < args.length;
  }

  private static Integer parsePositiveInt(String value, String optionName) {
    try {
      int parsed = Integer.parseInt(value);
      return parsed >= 0 ? parsed : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static CliArgs setError(CliArgs args, String message) {
    args.hasError = true;
    args.errorMessage = message;
    return args;
  }
}
