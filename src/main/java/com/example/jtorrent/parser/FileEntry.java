package com.example.jtorrent.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * File entry in a torrent.
 */
public class FileEntry {

  private final List<String> pathComponents;
  private final List<byte[]> pathComponentsRaw;
  private final long length;

  /**
   * Create a new file entry.
   *
   * @param pathComponents    the path components as strings
   * @param pathComponentsRaw the path components as raw bytes
   * @param length            the file length in bytes
   */
  public FileEntry(List<String> pathComponents, List<byte[]> pathComponentsRaw, long length) {
    this.pathComponents = pathComponents;
    this.pathComponentsRaw = pathComponentsRaw;
    this.length = length;
  }

  /**
   * Convenience constructor for creating a file entry from a path string.
   *
   * @param path   the file path (using / as separator)
   * @param length the file length in bytes
   */
  public FileEntry(String path, long length) {
    String[] parts = path.split("/");
    this.pathComponents = new ArrayList<>();
    this.pathComponentsRaw = new ArrayList<>();
    for (String part : parts) {
      this.pathComponents.add(part);
      this.pathComponentsRaw.add(part.getBytes(StandardCharsets.UTF_8));
    }
    this.length = length;
  }

  /**
   * Get the path components as strings.
   *
   * @return list of path components
   */
  public List<String> pathComponents() {
    return pathComponents;
  }

  /**
   * Get the path components as raw bytes (for non-UTF8 filenames).
   *
   * @return list of raw byte arrays
   */
  public List<byte[]> pathComponentsRaw() {
    return pathComponentsRaw;
  }

  /**
   * Get the full path as a string.
   *
   * @return the file path
   */
  public String path() {
    return String.join("/", pathComponents);
  }

  /**
   * Get the file length.
   *
   * @return length in bytes
   */
  public long length() {
    return length;
  }

  @Override
  public String toString() {
    return path() + " (" + length + " bytes)";
  }
}
