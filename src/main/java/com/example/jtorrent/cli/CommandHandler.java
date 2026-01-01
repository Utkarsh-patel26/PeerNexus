package com.example.jtorrent.cli;

/**
 * Interface for command handlers in the CLI application.
 */
@FunctionalInterface
public interface CommandHandler {
  /**
   * Execute the command with the given arguments.
   *
   * @param args the parsed command-line arguments
   */
  void execute(CliArgs args);
}
