package com.example.jtorrent.parser;

/**
 * Exception thrown when bencode parsing or encoding fails.
 */
public class BencodeException extends Exception {

  /**
   * Create a new BencodeException with the given message.
   *
   * @param message the error message
   */
  public BencodeException(String message) {
    super(message);
  }

  /**
   * Create a new BencodeException with the given message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public BencodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
